package com.ims.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseOrderRequest(
        @NotNull Long supplierId,
        @Valid List<Line> lines
) {
    public record Line(
            @NotNull Long productId,
            @Min(1) int qty,
            BigDecimal unitCost
    ) {
    }
}
