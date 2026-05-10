package com.interview.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.interview.common.event.DomainEvent;

import java.math.BigDecimal;

final class UnifiedSagaFixtures {

    static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private UnifiedSagaFixtures() {
    }

    record DeductPaymentPayload(
            String userId,
            String orderId,
            BigDecimal amount
    ) {
    }

    record RefundPaymentPayload(
            String userId,
            String orderId,
            BigDecimal amount
    ) {
    }

    record ReserveInventoryPayload(
            String sku,
            String orderId,
            int quantity
    ) {
    }

    record ReleaseInventoryPayload(
            String sku,
            String orderId,
            int quantity
    ) {
    }

    record CreditMerchantPayload(
            String merchantId,
            String orderId,
            BigDecimal amount
    ) {
    }

    static class PaymentDeducted extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentDeducted() {
            super();
        }

        PaymentDeducted(String userId, String orderId, BigDecimal amount) {
            super(userId, "UserAccount");
            this.userId = userId;
            this.amount = amount;
            setOrderId(orderId);
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    static class PaymentRefunded extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentRefunded() {
            super();
        }

        PaymentRefunded(String userId, String orderId, BigDecimal amount) {
            super(userId, "UserAccount");
            this.userId = userId;
            this.amount = amount;
            setOrderId(orderId);
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    static class InventoryReserved extends DomainEvent {
        private String sku;
        private int quantity;

        protected InventoryReserved() {
            super();
        }

        InventoryReserved(String orderId, String sku, int quantity) {
            super(orderId, "Product");
            this.sku = sku;
            this.quantity = quantity;
            setOrderId(orderId);
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    static class InventoryReserveFailed extends DomainEvent {
        private String sku;
        private int quantity;

        protected InventoryReserveFailed() {
            super();
        }

        InventoryReserveFailed(String orderId, String sku, int quantity) {
            super(orderId, "Product");
            this.sku = sku;
            this.quantity = quantity;
            setOrderId(orderId);
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    static class InventoryReleased extends DomainEvent {
        private String sku;
        private int quantity;

        protected InventoryReleased() {
            super();
        }

        InventoryReleased(String orderId, String sku, int quantity) {
            super(orderId, "Product");
            this.sku = sku;
            this.quantity = quantity;
            setOrderId(orderId);
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    static class PaymentDeductFailed extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentDeductFailed() {
            super();
        }

        PaymentDeductFailed(String userId, String orderId, BigDecimal amount) {
            super(userId, "UserAccount");
            this.userId = userId;
            this.amount = amount;
            setOrderId(orderId);
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    static class MerchantCredited extends DomainEvent {
        private String merchantId;
        private BigDecimal amount;

        protected MerchantCredited() {
            super();
        }

        MerchantCredited(String orderId, String merchantId, BigDecimal amount) {
            super(orderId, "MerchantAccount");
            this.merchantId = merchantId;
            this.amount = amount;
            setOrderId(orderId);
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }

    static class MerchantCreditFailed extends DomainEvent {
        private String merchantId;
        private BigDecimal amount;

        protected MerchantCreditFailed() {
            super();
        }

        MerchantCreditFailed(String orderId, String merchantId, BigDecimal amount) {
            super(orderId, "MerchantAccount");
            this.merchantId = merchantId;
            this.amount = amount;
            setOrderId(orderId);
        }

        public String getMerchantId() {
            return merchantId;
        }

        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}
