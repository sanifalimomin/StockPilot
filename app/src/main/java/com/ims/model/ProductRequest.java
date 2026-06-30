package com.ims.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String sku,
        @NotBlank String name,
        Long categoryId,
        Long supplierId,
        @PositiveOrZero BigDecimal unitCost,
        @Min(0) int reorderPoint,
        @Min(0) int reorderQty
) {
    public Product toEntity() {
        Product p = new Product();
        p.setSku(sku);
        p.setName(name);
        p.setCategoryId(categoryId);
        p.setSupplierId(supplierId);
        p.setUnitCost(unitCost != null ? unitCost : BigDecimal.ZERO);
        p.setReorderPoint(reorderPoint);
        p.setReorderQty(reorderQty);
        return p;
    }
}
