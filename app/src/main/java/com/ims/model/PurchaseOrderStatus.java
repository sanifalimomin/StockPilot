package com.ims.model;

import java.util.EnumSet;
import java.util.Set;

public enum PurchaseOrderStatus {
    DRAFT,
    ORDERED,
    RECEIVED,
    CANCELLED;

    public Set<PurchaseOrderStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(ORDERED, CANCELLED);
            case ORDERED -> EnumSet.of(RECEIVED, CANCELLED);
            case RECEIVED, CANCELLED -> EnumSet.noneOf(PurchaseOrderStatus.class);
        };
    }

    public boolean canTransitionTo(PurchaseOrderStatus next) {
        return allowedNext().contains(next);
    }
}
