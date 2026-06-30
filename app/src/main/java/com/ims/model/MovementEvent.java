package com.ims.model;


/** Event payload enqueued for movement processing. */
public record MovementEvent(
        String sku,
        Long warehouseId,
        MovementType type,
        int qty,
        Long fromWarehouseId,
        Long toWarehouseId,
        String idempotencyKey
) {
}
