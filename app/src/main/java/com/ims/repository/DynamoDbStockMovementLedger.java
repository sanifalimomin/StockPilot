package com.ims.repository;

import com.ims.model.MovementType;
import com.ims.model.StockMovement;

import com.ims.config.ImsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB-backed ledger for prod. Table is keyed on movementId (PK).
 * idempotencyKey dedupe uses a scan-with-filter for simplicity; in a production
 * deployment this would be a GSI on idempotencyKey.
 */
@Component
@ConditionalOnProperty(name = "ims.aws.enabled", havingValue = "true", matchIfMissing = true)
public class DynamoDbStockMovementLedger implements StockMovementLedger {

    private final DynamoDbClient client;
    private final String table;

    public DynamoDbStockMovementLedger(DynamoDbClient client, ImsProperties props) {
        this.client = client;
        this.table = props.getAws().getDynamodb().getTable();
    }

    @Override
    public void append(StockMovement m) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("movementId", s(m.getMovementId()));
        item.put("sku", s(m.getSku()));
        if (m.getWarehouseId() != null) item.put("warehouseId", n(m.getWarehouseId()));
        item.put("type", s(m.getType().name()));
        item.put("qty", n(m.getQty()));
        if (m.getFromWarehouseId() != null) item.put("fromWarehouseId", n(m.getFromWarehouseId()));
        if (m.getToWarehouseId() != null) item.put("toWarehouseId", n(m.getToWarehouseId()));
        item.put("timestamp", s(m.getTimestamp().toString()));
        if (m.getIdempotencyKey() != null) item.put("idempotencyKey", s(m.getIdempotencyKey()));

        client.putItem(PutItemRequest.builder().tableName(table).item(item).build());
    }

    @Override
    public Optional<StockMovement> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        var req = ScanRequest.builder()
                .tableName(table)
                .filterExpression("idempotencyKey = :k")
                .expressionAttributeValues(Map.of(":k", s(idempotencyKey)))
                .limit(1)
                .build();
        var res = client.scan(req);
        return res.items().stream().findFirst().map(this::fromItem);
    }

    @Override
    public List<StockMovement> recent(String sku, Long warehouseId, int limit) {
        var req = ScanRequest.builder().tableName(table).build();
        List<StockMovement> all = new ArrayList<>();
        client.scanPaginator(req).items().forEach(i -> all.add(fromItem(i)));
        return all.stream()
                .filter(m -> sku == null || sku.equals(m.getSku()))
                .filter(m -> warehouseId == null || warehouseId.equals(m.getWarehouseId()))
                .sorted(Comparator.comparing(StockMovement::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<StockMovement> outboundHistory(String sku, int limit) {
        return recent(sku, null, Integer.MAX_VALUE).stream()
                .filter(m -> m.getType() == MovementType.OUTBOUND)
                .limit(limit)
                .toList();
    }

    private StockMovement fromItem(Map<String, AttributeValue> i) {
        StockMovement m = new StockMovement();
        m.setMovementId(str(i, "movementId"));
        m.setSku(str(i, "sku"));
        if (i.containsKey("warehouseId")) m.setWarehouseId(Long.valueOf(i.get("warehouseId").n()));
        m.setType(MovementType.valueOf(str(i, "type")));
        if (i.containsKey("qty")) m.setQty(Integer.parseInt(i.get("qty").n()));
        if (i.containsKey("fromWarehouseId")) m.setFromWarehouseId(Long.valueOf(i.get("fromWarehouseId").n()));
        if (i.containsKey("toWarehouseId")) m.setToWarehouseId(Long.valueOf(i.get("toWarehouseId").n()));
        if (i.containsKey("timestamp")) m.setTimestamp(Instant.parse(str(i, "timestamp")));
        m.setIdempotencyKey(str(i, "idempotencyKey"));
        return m;
    }

    private static String str(Map<String, AttributeValue> i, String k) {
        AttributeValue v = i.get(k);
        return v == null ? null : v.s();
    }

    private static AttributeValue s(String v) { return AttributeValue.builder().s(v).build(); }
    private static AttributeValue n(Number v) { return AttributeValue.builder().n(String.valueOf(v)).build(); }
}
