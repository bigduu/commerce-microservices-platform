package com.interview.order.domain;

public enum OrderStatus {
    PENDING,
    PAYMENT_PROCESSING,
    INVENTORY_PROCESSING,
    MERCHANT_CREDITING,
    COMPLETED,
    FAILED,
    CANCELLED
}
