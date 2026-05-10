package com.interview.user.domain;

import com.interview.common.domain.AggregateRoot;
import com.interview.common.domain.Money;
import com.interview.common.event.DomainEvent;
import com.interview.common.exception.InsufficientBalanceException;
import com.interview.user.domain.events.UserAccountEvents;

import java.math.BigDecimal;

public class UserAccount extends AggregateRoot {

    private String userId;
    private String username;
    private Money balance = Money.zero();

    public UserAccount() {}

    private UserAccount(String userId, String username) {
        super(userId);
        this.userId = userId;
        this.username = username;
    }

    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public Money getBalance() { return balance; }

    public static UserAccount create(String userId, String username) {
        UserAccount account = new UserAccount(userId, username);
        account.apply(new UserAccountEvents.AccountCreated(userId, username));
        return account;
    }

    public void topUp(Money amount) {
        if (amount == null || !amount.isGreaterThan(Money.zero())) {
            throw new IllegalArgumentException("Top-up amount must be greater than zero");
        }
        apply(new UserAccountEvents.AccountToppedUp(userId, amount.amount()));
    }

    public void deductPayment(String orderId, Money amount) {
        if (amount == null || !amount.isGreaterThan(Money.zero())) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (!balance.isGreaterThanOrEqual(amount)) {
            throw new InsufficientBalanceException(
                    "Insufficient balance for user " + userId + ": " + balance.amount() + " < " + amount.amount());
        }
        apply(new UserAccountEvents.PaymentDeducted(userId, orderId, amount.amount()));
    }

    public void refundPayment(String orderId, Money amount) {
        if (amount == null || !amount.isGreaterThan(Money.zero())) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
        apply(new UserAccountEvents.PaymentRefunded(userId, orderId, amount.amount()));
    }

    @Override
    protected void replay(DomainEvent event) {
        switch (event) {
            case UserAccountEvents.AccountCreated e -> {
                this.userId = e.getUserId();
                this.username = e.getUsername();
                this.balance = Money.zero();
            }
            case UserAccountEvents.AccountToppedUp e -> {
                this.balance = this.balance.add(Money.of(e.getAmount()));
            }
            case UserAccountEvents.PaymentDeducted e -> {
                this.balance = this.balance.subtract(Money.of(e.getAmount()));
            }
            case UserAccountEvents.PaymentRefunded e -> {
                this.balance = this.balance.add(Money.of(e.getAmount()));
            }
            default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
        }
    }

}
