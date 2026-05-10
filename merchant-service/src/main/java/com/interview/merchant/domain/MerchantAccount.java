package com.interview.merchant.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "merchant_accounts")
public class MerchantAccount {

    @Id
    private String merchantId;

    private String merchantName;

    private BigDecimal balance;

    @Version
    private Long version;

    protected MerchantAccount() {}

    public MerchantAccount(String merchantId, String merchantName) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.balance = BigDecimal.ZERO;
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
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

    public Long getVersion() {
        return version;
    }
}
