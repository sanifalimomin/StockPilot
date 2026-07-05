package com.ims.model;

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
