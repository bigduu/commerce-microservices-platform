package com.interview.common.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount) {

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Money scale cannot exceed 2 decimal places");
        }
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount.setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount).setScale(2, BigDecimal.ROUND_HALF_UP));
    }

    private static final Money ZERO = new Money(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));

    public static Money zero() {
        return ZERO;
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return amount.compareTo(other.amount) >= 0;
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(other.amount) > 0;
    }
}
