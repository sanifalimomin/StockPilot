package com.ims.model;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderResponse(
        Long id,
        Long supplierId,
        PurchaseOrderStatus status,
        Instant createdAt,
        List<Line> lines
) {
    public record Line(Long id, Long productId, int qty, BigDecimal unitCost) {
    }

    public static PurchaseOrderResponse from(PurchaseOrder po) {
        List<Line> lines = po.getLines().stream()
                .map(l -> new Line(l.getId(), l.getProductId(), l.getQty(), l.getUnitCost()))
                .toList();
        return new PurchaseOrderResponse(po.getId(), po.getSupplierId(), po.getStatus(),
                po.getCreatedAt(), lines);
    }
}
