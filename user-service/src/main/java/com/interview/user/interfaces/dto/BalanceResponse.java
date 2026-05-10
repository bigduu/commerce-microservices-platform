package com.interview.user.interfaces.dto;

import java.math.BigDecimal;

public class BalanceResponse {

    private String userId;
    private BigDecimal balance;

    public BalanceResponse() {}

    public BalanceResponse(String userId, BigDecimal balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
