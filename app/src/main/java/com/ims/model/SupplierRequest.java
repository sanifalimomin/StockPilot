package com.ims.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank String name,
        @Email String email,
        String phone
) {
    public Supplier toEntity() {
        Supplier s = new Supplier();
        s.setName(name);
        s.setEmail(email);
        s.setPhone(phone);
        return s;
    }
}
