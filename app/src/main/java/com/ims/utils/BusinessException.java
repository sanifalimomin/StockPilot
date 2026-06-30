package com.ims.utils;

/** Thrown for invalid business operations (e.g. negative stock, illegal PO transition). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
