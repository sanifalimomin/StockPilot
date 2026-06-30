package com.ims.services;

import com.ims.repository.ReportStore;

import com.ims.model.InventoryLevel;
import com.ims.model.Product;
import com.ims.model.Warehouse;
import com.ims.repository.InventoryLevelRepository;
import com.ims.repository.ProductRepository;
import com.ims.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final InventoryLevelRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final WarehouseRepository warehouseRepo;
    private final ReportStore store;

    public ReportService(InventoryLevelRepository inventoryRepo,
                         ProductRepository productRepo,
                         WarehouseRepository warehouseRepo,
                         ReportStore store) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.warehouseRepo = warehouseRepo;
        this.store = store;
    }

    @Transactional(readOnly = true)
    public Map<String, String> generateValuation() {
        Map<Long, Product> products = productRepo.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Warehouse> warehouses = warehouseRepo.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        StringBuilder csv = new StringBuilder();
        csv.append("sku,product,warehouse,quantityOnHand,unitCost,valuation\n");
        BigDecimal grandTotal = BigDecimal.ZERO;

        List<InventoryLevel> levels = inventoryRepo.findAll();
        for (InventoryLevel level : levels) {
            Product p = products.get(level.getProductId());
            Warehouse w = warehouses.get(level.getWarehouseId());
            if (p == null) {
                continue;
            }
            BigDecimal value = p.getUnitCost().multiply(BigDecimal.valueOf(level.getQuantityOnHand()));
            grandTotal = grandTotal.add(value);
            csv.append(escape(p.getSku())).append(',')
                    .append(escape(p.getName())).append(',')
                    .append(escape(w != null ? w.getCode() : String.valueOf(level.getWarehouseId()))).append(',')
                    .append(level.getQuantityOnHand()).append(',')
                    .append(p.getUnitCost().toPlainString()).append(',')
                    .append(value.toPlainString()).append('\n');
        }
        csv.append("TOTAL,,,,,").append(grandTotal.toPlainString()).append('\n');

        String reportId = UUID.randomUUID().toString();
        String location = store.store(reportId, "valuation.csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
        return Map.of("reportId", reportId, "location", location);
    }

    public List<ReportStore.ReportDescriptor> listReports() {
        return store.list();
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
