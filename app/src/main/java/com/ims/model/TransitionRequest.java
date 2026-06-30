package com.ims.model;

import jakarta.validation.constraints.NotNull;

public record TransitionRequest(@NotNull PurchaseOrderStatus status) {
}
