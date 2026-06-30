package com.ims.services;

import com.ims.model.InventoryLevel;
import com.ims.model.InventoryRowDto;
import com.ims.repository.InventoryLevelRepository;
import com.ims.model.ConsolidatedStockDto;
import com.ims.utils.NotFoundException;

import com.ims.model.Product;
import com.ims.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryLevelRepository inventoryRepo;
    private final ProductRepository productRepo;

    public InventoryService(InventoryLevelRepository inventoryRepo, ProductRepository productRepo) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
    }

    public List<InventoryRowDto> rows(Long warehouseId, String sku) {
        List<InventoryLevel> levels;
        if (sku != null) {
            Product p = productRepo.findBySku(sku)
                    .orElseThrow(() -> new NotFoundException("Unknown SKU: " + sku));
            levels = inventoryRepo.findByProductId(p.getId());
        } else if (warehouseId != null) {
            levels = inventoryRepo.findByWarehouseId(warehouseId);
        } else {
            levels = inventoryRepo.findAll();
        }

        Map<Long, Product> products = productRepo.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<InventoryRowDto> result = new ArrayList<>();
        for (InventoryLevel l : levels) {
            if (warehouseId != null && !warehouseId.equals(l.getWarehouseId())) {
                continue;
            }
            Product p = products.get(l.getProductId());
            result.add(new InventoryRowDto(
                    p != null ? p.getSku() : null,
                    l.getProductId(),
                    l.getWarehouseId(),
                    l.getQuantityOnHand(),
                    l.getQuantityReserved()));
        }
        return result;
    }

    @Cacheable(cacheNames = "consolidatedStock", key = "#sku")
    public ConsolidatedStockDto consolidated(String sku) {
        Product p = productRepo.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Unknown SKU: " + sku));
        int total = inventoryRepo.findByProductId(p.getId()).stream()
                .mapToInt(InventoryLevel::getQuantityOnHand)
                .sum();
        return new ConsolidatedStockDto(sku, p.getId(), total);
    }
}
