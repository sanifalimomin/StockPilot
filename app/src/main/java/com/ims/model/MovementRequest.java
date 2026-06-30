package com.ims.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MovementRequest(
        @NotBlank String sku,
        Long warehouseId,
        @NotNull MovementType type,
        @Min(1) int qty,
        Long fromWarehouseId,
        Long toWarehouseId,
        String idempotencyKey
) {
}
