package com.ims.model;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank String name,
        String description
) {
    public Category toEntity() {
        Category c = new Category();
        c.setName(name);
        c.setDescription(description);
        return c;
    }
}
