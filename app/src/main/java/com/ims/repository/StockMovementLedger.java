package com.ims.repository;

import com.ims.model.StockMovement;

import java.util.List;
import java.util.Optional;

/**
 * Port for the append-only stock movement ledger.
 * Implementations: DynamoDbStockMovementLedger (prod), InMemoryStockMovementLedger (local).
 */
public interface StockMovementLedger {

    /** Append a movement to the ledger. */
    void append(StockMovement movement);

    /** Look up an existing movement by its idempotency key, if any. */
    Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);

    /** Recent movements filtered by optional sku/warehouse, newest first, capped at limit. */
    List<StockMovement> recent(String sku, Long warehouseId, int limit);

    /** OUTBOUND movement history for a SKU (used by forecasting), newest first. */
    List<StockMovement> outboundHistory(String sku, int limit);
}
