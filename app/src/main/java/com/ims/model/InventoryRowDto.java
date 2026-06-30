package com.ims.model;

public record InventoryRowDto(
        String sku,
        Long productId,
        Long warehouseId,
        int quantityOnHand,
        int quantityReserved
) {
}
