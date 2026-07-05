package com.ims.services;

import com.ims.repository.ReportStore;

import com.ims.model.InventoryLevel;
import com.ims.model.Product;
import com.ims.model.StockMovement;
import com.ims.model.Supplier;
import com.ims.model.Warehouse;
import com.ims.repository.InventoryLevelRepository;
import com.ims.repository.ProductRepository;
import com.ims.repository.StockMovementLedger;
import com.ims.repository.SupplierRepository;
import com.ims.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int MOVEMENT_FETCH_LIMIT = 10_000;

    private final InventoryLevelRepository inventoryRepo;
    private final ProductRepository productRepo;
    private final WarehouseRepository warehouseRepo;
    private final SupplierRepository supplierRepo;
    private final StockMovementLedger ledger;
    private final ReportStore store;

    public ReportService(InventoryLevelRepository inventoryRepo,
                         ProductRepository productRepo,
                         WarehouseRepository warehouseRepo,
                         SupplierRepository supplierRepo,
                         StockMovementLedger ledger,
                         ReportStore store) {
        this.inventoryRepo = inventoryRepo;
        this.productRepo = productRepo;
        this.warehouseRepo = warehouseRepo;
        this.supplierRepo = supplierRepo;
        this.ledger = ledger;
        this.store = store;
    }

    public List<Map<String, String>> generateDailyReports() {
        List<Map<String, String>> results = new ArrayList<>();
        results.add(generateValuation());
        results.add(generateLowStock());
        results.add(generateMovements());
        log.info("Generated {} daily reports", results.size());
        return results;
    }

    @Transactional(readOnly = true)
    public Map<String, String> generateValuation() {
        Map<Long, Product> products = byId(productRepo.findAll(), Product::getId);
        Map<Long, Warehouse> warehouses = byId(warehouseRepo.findAll(), Warehouse::getId);

        StringBuilder csv = new StringBuilder();
        csv.append("sku,product,warehouse,quantityOnHand,unitCost,valuation\n");
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (InventoryLevel level : inventoryRepo.findAll()) {
            Product p = products.get(level.getProductId());
            if (p == null) {
                continue;
            }
            Warehouse w = warehouses.get(level.getWarehouseId());
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
        return storeReport("valuation.csv", csv.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, String> generateLowStock() {
        Map<Long, Product> products = byId(productRepo.findAll(), Product::getId);
        Map<Long, Warehouse> warehouses = byId(warehouseRepo.findAll(), Warehouse::getId);
        Map<Long, Supplier> suppliers = byId(supplierRepo.findAll(), Supplier::getId);

        StringBuilder csv = new StringBuilder();
        csv.append("sku,product,warehouse,quantityOnHand,reorderPoint,suggestedOrderQty,supplier\n");

        for (InventoryLevel level : inventoryRepo.findAll()) {
            Product p = products.get(level.getProductId());
            if (p == null || level.getQuantityOnHand() > p.getReorderPoint()) {
                continue;
            }
            Warehouse w = warehouses.get(level.getWarehouseId());
            Supplier s = p.getSupplierId() != null ? suppliers.get(p.getSupplierId()) : null;
            csv.append(escape(p.getSku())).append(',')
                    .append(escape(p.getName())).append(',')
                    .append(escape(w != null ? w.getCode() : String.valueOf(level.getWarehouseId()))).append(',')
                    .append(level.getQuantityOnHand()).append(',')
                    .append(p.getReorderPoint()).append(',')
                    .append(p.getReorderQty()).append(',')
                    .append(escape(s != null ? s.getName() : "")).append('\n');
        }
        return storeReport("low-stock.csv", csv.toString());
    }

    public Map<String, String> generateMovements() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));

        StringBuilder csv = new StringBuilder();
        csv.append("movementId,timestamp,sku,type,qty,warehouseId,fromWarehouseId,toWarehouseId\n");

        for (StockMovement m : ledger.recent(null, null, MOVEMENT_FETCH_LIMIT)) {
            if (m.getTimestamp() == null || m.getTimestamp().isBefore(cutoff)) {
                continue;
            }
            csv.append(escape(m.getMovementId())).append(',')
                    .append(m.getTimestamp()).append(',')
                    .append(escape(m.getSku())).append(',')
                    .append(m.getType()).append(',')
                    .append(m.getQty()).append(',')
                    .append(nullable(m.getWarehouseId())).append(',')
                    .append(nullable(m.getFromWarehouseId())).append(',')
                    .append(nullable(m.getToWarehouseId())).append('\n');
        }
        return storeReport("movements-24h.csv", csv.toString());
    }

    public List<ReportStore.ReportDescriptor> listReports() {
        return store.list();
    }

    private Map<String, String> storeReport(String filename, String csv) {
        String reportId = UUID.randomUUID().toString();
        ReportStore.ReportDescriptor stored =
                store.store(reportId, filename, csv.getBytes(StandardCharsets.UTF_8));
        Map<String, String> response = new LinkedHashMap<>();
        response.put("reportId", reportId);
        response.put("filename", filename);
        response.put("location", stored.location());
        if (stored.downloadUrl() != null) {
            response.put("downloadUrl", stored.downloadUrl());
        }
        return response;
    }

    private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static String nullable(Long v) {
        return v == null ? "" : String.valueOf(v);
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
