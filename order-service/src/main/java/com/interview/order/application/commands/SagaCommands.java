package com.interview.order.application.commands;

import java.math.BigDecimal;

public final class SagaCommands {

    private SagaCommands() {
    }

    public record DeductPaymentPayload(
            String userId,
            String orderId,
            BigDecimal amount
    ) {
    }

    public record RefundPaymentPayload(
            String userId,
            String orderId,
            BigDecimal amount
    ) {
    }

    public record ReserveInventoryPayload(
            String sku,
            String orderId,
            int quantity
    ) {
    }

    public record ReleaseInventoryPayload(
            String sku,
            String orderId,
            int quantity
    ) {
    }

    public record CreditMerchantPayload(
            String merchantId,
            String orderId,
            BigDecimal amount
    ) {
    }

    public record DebitMerchantPayload(
            String merchantId,
            String orderId,
            BigDecimal amount
    ) {
    }
}
