package com.ims.repository;

import com.ims.model.MovementType;
import com.ims.model.StockMovement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "ims.aws.enabled", havingValue = "false")
public class InMemoryStockMovementLedger implements StockMovementLedger {

    private final List<StockMovement> movements = new CopyOnWriteArrayList<>();
    private final Map<String, StockMovement> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public synchronized void append(StockMovement movement) {
        movements.add(movement);
        if (movement.getIdempotencyKey() != null) {
            byIdempotencyKey.put(movement.getIdempotencyKey(), movement);
        }
    }

    @Override
    public Optional<StockMovement> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public List<StockMovement> recent(String sku, Long warehouseId, int limit) {
        return movements.stream()
                .filter(m -> sku == null || sku.equals(m.getSku()))
                .filter(m -> warehouseId == null || warehouseId.equals(m.getWarehouseId()))
                .sorted(Comparator.comparing(StockMovement::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<StockMovement> outboundHistory(String sku, int limit) {
        return movements.stream()
                .filter(m -> m.getType() == MovementType.OUTBOUND)
                .filter(m -> sku == null || sku.equals(m.getSku()))
                .sorted(Comparator.comparing(StockMovement::getTimestamp).reversed())
                .limit(limit)
                .toList();
    }
}
