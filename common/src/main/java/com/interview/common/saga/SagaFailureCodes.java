package com.interview.common.saga;

public final class SagaFailureCodes {

    private SagaFailureCodes() {
    }

    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    public static final String INSUFFICIENT_INVENTORY = "INSUFFICIENT_INVENTORY";
    public static final String MERCHANT_CREDIT_FAILED = "MERCHANT_CREDIT_FAILED";
    public static final String MERCHANT_DEBIT_FAILED = "MERCHANT_DEBIT_FAILED";
    public static final String PAYMENT_REFUND_FAILED = "PAYMENT_REFUND_FAILED";
    public static final String INVENTORY_RELEASE_FAILED = "INVENTORY_RELEASE_FAILED";
    public static final String INVALID_COMMAND = "INVALID_COMMAND";
}
