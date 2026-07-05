package com.ims.model;

import java.time.Instant;

public record MovementResponse(
        String movementId,
        String sku,
        Long warehouseId,
        MovementType type,
        int qty,
        Long fromWarehouseId,
        Long toWarehouseId,
        Instant timestamp,
        String idempotencyKey,
        boolean processedSync
) {
}
