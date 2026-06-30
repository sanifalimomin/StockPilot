package com.ims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "reorder_point", nullable = false)
    private int reorderPoint;

    @Column(name = "reorder_qty", nullable = false)
    private int reorderQty;
}
