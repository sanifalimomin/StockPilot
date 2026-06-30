package com.ims.services;

import com.ims.model.LowStockAlert;
import com.ims.repository.LowStockAlertRepository;

import com.ims.model.InventoryLevel;
import com.ims.model.Product;
import com.ims.repository.InventoryLevelRepository;
import com.ims.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReorderService {

    private static final Logger log = LoggerFactory.getLogger(ReorderService.class);

    private final InventoryLevelRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final LowStockAlertRepository alertRepo;
    private final Notifier notifier;

    public ReorderService(InventoryLevelRepository inventoryRepo,
                          ProductRepository productRepo,
                          LowStockAlertRepository alertRepo,
                          Notifier notifier) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.alertRepo = alertRepo;
        this.notifier = notifier;
    }

    /** Check a single inventory level and raise an alert if at/below reorder point. */
    @Transactional
    public void evaluate(InventoryLevel level) {
        Product product = productRepo.findById(level.getProductId()).orElse(null);
        if (product == null) {
            return;
        }
        if (level.getQuantityOnHand() <= product.getReorderPoint()) {
            raiseAlert(product, level);
        } else {
            // recovered: resolve any open alert for this product/warehouse
            alertRepo.findFirstByProductIdAndWarehouseIdAndResolvedFalse(
                    level.getProductId(), level.getWarehouseId())
                    .ifPresent(a -> {
                        a.setResolved(true);
                        alertRepo.save(a);
                    });
        }
    }

    private void raiseAlert(Product product, InventoryLevel level) {
        boolean exists = alertRepo
                .findFirstByProductIdAndWarehouseIdAndResolvedFalse(level.getProductId(), level.getWarehouseId())
                .isPresent();
        if (exists) {
            return; // already alerted; avoid duplicates
        }
        LowStockAlert alert = new LowStockAlert();
        alert.setProductId(product.getId());
        alert.setWarehouseId(level.getWarehouseId());
        alert.setCurrentQty(level.getQuantityOnHand());
        alert.setReorderPoint(product.getReorderPoint());
        alert.setCreatedAt(Instant.now());
        alert.setResolved(false);
        alertRepo.save(alert);

        String subject = "Low stock: " + product.getSku();
        String message = String.format(
                "Product %s (%s) at warehouse %d is low: onHand=%d <= reorderPoint=%d. Suggested PO qty=%d.",
                product.getSku(), product.getName(), level.getWarehouseId(),
                level.getQuantityOnHand(), product.getReorderPoint(), product.getReorderQty());
        log.info("Raising low-stock alert: {}", message);
        notifier.notifyLowStock(subject, message);
    }

    /** Full scan across all inventory rows; used by the scheduled task. */
    @Transactional
    public int scanAll() {
        List<InventoryLevel> levels = inventoryRepo.findAll();
        int before = alertRepo.findByResolved(false).size();
        levels.forEach(this::evaluate);
        int after = alertRepo.findByResolved(false).size();
        log.info("Reorder scan complete: {} inventory rows, open alerts {} -> {}", levels.size(), before, after);
        return after;
    }
}
