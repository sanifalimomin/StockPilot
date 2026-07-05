package com.ims.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    private String movementId;
    private String sku;
    private Long warehouseId;
    private MovementType type;
    private int qty;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private Instant timestamp;
    private String idempotencyKey;
}
