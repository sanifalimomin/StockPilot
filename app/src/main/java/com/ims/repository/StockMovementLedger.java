package com.ims.repository;

import com.ims.model.StockMovement;

import java.util.List;
import java.util.Optional;

public interface StockMovementLedger {

    void append(StockMovement movement);

    Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);

    List<StockMovement> recent(String sku, Long warehouseId, int limit);

    List<StockMovement> outboundHistory(String sku, int limit);
}
