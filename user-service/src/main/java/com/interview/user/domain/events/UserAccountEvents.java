package com.interview.user.domain.events;

import com.interview.common.event.DomainEvent;

import java.math.BigDecimal;

public class UserAccountEvents {

    private UserAccountEvents() {}

    public static class AccountCreated extends DomainEvent {
        private String userId;
        private String username;

        protected AccountCreated() {}

        public AccountCreated(String userId, String username) {
            super(userId, "UserAccount");
            this.userId = userId;
            this.username = username;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    public static class AccountToppedUp extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected AccountToppedUp() {}

        public AccountToppedUp(String userId, BigDecimal amount) {
            super(userId, "UserAccount");
            this.userId = userId;
            this.amount = amount;
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

    public static class PaymentDeducted extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentDeducted() {}

        public PaymentDeducted(String userId, String orderId, BigDecimal amount) {
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

    public static class PaymentDeductFailed extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentDeductFailed() {}

        public PaymentDeductFailed(String userId, String orderId, BigDecimal amount) {
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

    public static class PaymentRefunded extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentRefunded() {}

        public PaymentRefunded(String userId, String orderId, BigDecimal amount) {
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

    public static class PaymentRefundFailed extends DomainEvent {
        private String userId;
        private BigDecimal amount;

        protected PaymentRefundFailed() {}

        public PaymentRefundFailed(String userId, String orderId, BigDecimal amount) {
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
}
