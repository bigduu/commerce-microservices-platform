package com.interview.order.domain;

public enum SagaStep {
    DEDUCT_PAYMENT,
    RESERVE_INVENTORY,
    CREDIT_MERCHANT
}
