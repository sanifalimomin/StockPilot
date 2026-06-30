package com.ims.model;

import java.io.Serializable;

public record ConsolidatedStockDto(
        String sku,
        Long productId,
        int totalOnHand
) implements Serializable {
}
