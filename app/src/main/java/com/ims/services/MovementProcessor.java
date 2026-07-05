package com.ims.services;

import com.ims.model.MovementType;
import com.ims.repository.StockMovementLedger;
import com.ims.model.StockMovement;
import com.ims.model.MovementEvent;
import com.ims.utils.BusinessException;
import com.ims.utils.NotFoundException;

import com.ims.services.ReorderService;
import com.ims.model.Product;
import com.ims.repository.ProductRepository;
import com.ims.model.InventoryLevel;
import com.ims.repository.InventoryLevelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MovementProcessor {

    private static final Logger log = LoggerFactory.getLogger(MovementProcessor.class);

    private final ProductRepository productRepo;
    private final InventoryLevelRepository inventoryRepo;
    private final StockMovementLedger ledger;
    private final ReorderService reorderService;

    public MovementProcessor(ProductRepository productRepo,
                             InventoryLevelRepository inventoryRepo,
                             StockMovementLedger ledger,
                             ReorderService reorderService) {
        this.productRepo = productRepo;
        this.inventoryRepo = inventoryRepo;
        this.ledger = ledger;
        this.reorderService = reorderService;
    }

    @Transactional
    @CacheEvict(cacheNames = "consolidatedStock", key = "#event.sku()")
    public StockMovement process(MovementEvent event) {

        if (event.idempotencyKey() != null) {
            var existing = ledger.findByIdempotencyKey(event.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate movement ignored (idempotencyKey={})", event.idempotencyKey());
                return existing.get();
            }
        }

        Product product = productRepo.findBySku(event.sku())
                .orElseThrow(() -> new NotFoundException("Unknown SKU: " + event.sku()));

        if (event.qty() <= 0) {
            throw new BusinessException("qty must be positive");
        }

        switch (event.type()) {
            case INBOUND -> applyDelta(product.getId(), event.warehouseId(), event.qty());
            case OUTBOUND -> applyDelta(product.getId(), event.warehouseId(), -event.qty());
            case ADJUSTMENT -> applyAdjustment(product.getId(), event.warehouseId(), event.qty());
            case TRANSFER -> applyTransfer(product.getId(), event);
        }

        StockMovement movement = new StockMovement();
        movement.setMovementId(UUID.randomUUID().toString());
        movement.setSku(event.sku());
        movement.setWarehouseId(event.warehouseId());
        movement.setType(event.type());
        movement.setQty(event.qty());
        movement.setFromWarehouseId(event.fromWarehouseId());
        movement.setToWarehouseId(event.toWarehouseId());
        movement.setTimestamp(Instant.now());
        movement.setIdempotencyKey(event.idempotencyKey());
        ledger.append(movement);

        evaluateAffected(product.getId(), event);

        log.info("Processed movement {} type={} sku={} qty={}",
                movement.getMovementId(), event.type(), event.sku(), event.qty());
        return movement;
    }

    private void evaluateAffected(Long productId, MovementEvent event) {
        if (event.type() == MovementType.TRANSFER) {
            inventoryRepo.findByProductIdAndWarehouseId(productId, event.fromWarehouseId())
                    .ifPresent(reorderService::evaluate);
            inventoryRepo.findByProductIdAndWarehouseId(productId, event.toWarehouseId())
                    .ifPresent(reorderService::evaluate);
        } else {
            inventoryRepo.findByProductIdAndWarehouseId(productId, event.warehouseId())
                    .ifPresent(reorderService::evaluate);
        }
    }

    private InventoryLevel levelOrCreate(Long productId, Long warehouseId) {
        if (warehouseId == null) {
            throw new BusinessException("warehouseId is required");
        }
        return inventoryRepo.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> {
                    InventoryLevel l = new InventoryLevel();
                    l.setProductId(productId);
                    l.setWarehouseId(warehouseId);
                    l.setQuantityOnHand(0);
                    l.setQuantityReserved(0);
                    return l;
                });
    }

    private void applyDelta(Long productId, Long warehouseId, int delta) {
        InventoryLevel level = levelOrCreate(productId, warehouseId);
        int updated = level.getQuantityOnHand() + delta;
        if (updated < 0) {
            throw new BusinessException("OUTBOUND would drive on-hand below zero (have "
                    + level.getQuantityOnHand() + ", requested " + (-delta) + ")");
        }
        level.setQuantityOnHand(updated);
        inventoryRepo.save(level);
    }

    private void applyAdjustment(Long productId, Long warehouseId, int qty) {
        InventoryLevel level = levelOrCreate(productId, warehouseId);
        level.setQuantityOnHand(qty);
        inventoryRepo.save(level);
    }

    private void applyTransfer(Long productId, MovementEvent event) {
        if (event.fromWarehouseId() == null || event.toWarehouseId() == null) {
            throw new BusinessException("TRANSFER requires fromWarehouseId and toWarehouseId");
        }
        if (event.fromWarehouseId().equals(event.toWarehouseId())) {
            throw new BusinessException("TRANSFER from and to warehouse must differ");
        }
        applyDelta(productId, event.fromWarehouseId(), -event.qty());
        applyDelta(productId, event.toWarehouseId(), event.qty());
    }
}
