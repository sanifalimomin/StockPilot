package com.ims.model;

import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(
        @NotBlank String code,
        @NotBlank String name,
        String region
) {
    public Warehouse toEntity() {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(name);
        w.setRegion(region);
        return w;
    }
}
