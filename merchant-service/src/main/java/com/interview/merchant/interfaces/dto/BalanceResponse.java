package com.interview.merchant.interfaces.dto;

import java.math.BigDecimal;

public class BalanceResponse {

    private String merchantId;
    private BigDecimal balance;

    public BalanceResponse(String merchantId, BigDecimal balance) {
        this.merchantId = merchantId;
        this.balance = balance;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
