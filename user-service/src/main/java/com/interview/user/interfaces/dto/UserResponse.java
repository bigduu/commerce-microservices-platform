package com.interview.user.interfaces.dto;

import java.math.BigDecimal;

public class UserResponse {

    private String userId;
    private String username;
    private BigDecimal balance;

    public UserResponse() {}

    public UserResponse(String userId, String username, BigDecimal balance) {
        this.userId = userId;
        this.username = username;
        this.balance = balance;
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

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
