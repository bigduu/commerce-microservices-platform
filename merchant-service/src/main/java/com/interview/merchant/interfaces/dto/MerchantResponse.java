package com.interview.merchant.interfaces.dto;

import java.math.BigDecimal;

public class MerchantResponse {

    private String merchantId;
    private String merchantName;
    private BigDecimal balance;

    public MerchantResponse(String merchantId, String merchantName, BigDecimal balance) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.balance = balance;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
