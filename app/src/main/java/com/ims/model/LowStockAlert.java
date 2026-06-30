package com.ims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "low_stock_alert")
public class LowStockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "current_qty", nullable = false)
    private int currentQty;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private boolean resolved;
}
