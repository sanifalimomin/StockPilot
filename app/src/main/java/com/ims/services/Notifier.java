package com.ims.services;

/** Port for publishing low-stock notifications (SNS in prod, log no-op locally). */
public interface Notifier {
    void notifyLowStock(String subject, String message);
}
