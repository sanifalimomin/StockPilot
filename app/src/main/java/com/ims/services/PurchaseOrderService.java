package com.ims.services;

import com.ims.model.PurchaseOrderLine;
import com.ims.model.PurchaseOrderStatus;
import com.ims.model.PurchaseOrderRequest;
import com.ims.repository.PurchaseOrderRepository;
import com.ims.model.PurchaseOrder;
import com.ims.utils.BusinessException;
import com.ims.utils.NotFoundException;

import com.ims.model.InventoryLevel;
import com.ims.model.Product;
import com.ims.repository.InventoryLevelRepository;
import com.ims.repository.ProductRepository;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository poRepo;
    private final ProductRepository productRepo;
    private final InventoryLevelRepository inventoryRepo;
    private final CacheManager cacheManager;

    public PurchaseOrderService(PurchaseOrderRepository poRepo,
                                ProductRepository productRepo,
                                InventoryLevelRepository inventoryRepo,
                                CacheManager cacheManager) {
        this.poRepo = poRepo;
        this.productRepo = productRepo;
        this.inventoryRepo = inventoryRepo;
        this.cacheManager = cacheManager;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> findAll() {
        return poRepo.findAll();
    }

    @Transactional(readOnly = true)
    public PurchaseOrder findById(Long id) {
        return poRepo.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("PurchaseOrder not found: " + id));
    }

    public PurchaseOrder create(PurchaseOrderRequest req) {
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplierId(req.supplierId());
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setCreatedAt(Instant.now());
        if (req.lines() != null) {
            for (PurchaseOrderRequest.Line l : req.lines()) {
                Product p = productRepo.findById(l.productId())
                        .orElseThrow(() -> new NotFoundException("Product not found: " + l.productId()));
                PurchaseOrderLine line = new PurchaseOrderLine();
                line.setProductId(p.getId());
                line.setQty(l.qty());
                line.setUnitCost(l.unitCost() != null ? l.unitCost() : p.getUnitCost());
                po.addLine(line);
            }
        }
        return poRepo.save(po);
    }

    public PurchaseOrder transition(Long id, PurchaseOrderStatus target) {
        PurchaseOrder po = findById(id);
        if (po.getStatus() == target) {
            return po;
        }
        if (!po.getStatus().canTransitionTo(target)) {
            throw new BusinessException("Illegal PO transition " + po.getStatus() + " -> " + target);
        }
        if (target == PurchaseOrderStatus.RECEIVED) {
            receiveStock(po);
        }
        po.setStatus(target);
        return poRepo.save(po);
    }

    private void receiveStock(PurchaseOrder po) {
        Long defaultWarehouseId = inventoryRepo.findAll().stream()
                .map(InventoryLevel::getWarehouseId)
                .findFirst()
                .orElse(1L);
        for (PurchaseOrderLine line : po.getLines()) {
            InventoryLevel level = inventoryRepo
                    .findByProductIdAndWarehouseId(line.getProductId(), defaultWarehouseId)
                    .orElseGet(() -> {
                        InventoryLevel l = new InventoryLevel();
                        l.setProductId(line.getProductId());
                        l.setWarehouseId(defaultWarehouseId);
                        return l;
                    });
            level.setQuantityOnHand(level.getQuantityOnHand() + line.getQty());
            inventoryRepo.save(level);
            evictConsolidated(line.getProductId());
        }
    }

    private void evictConsolidated(Long productId) {
        var cache = cacheManager.getCache("consolidatedStock");
        if (cache != null) {
            productRepo.findById(productId).map(Product::getSku)
                    .filter(Objects::nonNull)
                    .ifPresent(cache::evict);
        }
    }
}
