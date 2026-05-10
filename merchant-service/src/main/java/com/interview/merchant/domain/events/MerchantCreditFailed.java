package com.interview.merchant.domain.events;

import com.interview.common.event.DomainEvent;

import java.math.BigDecimal;

public class MerchantCreditFailed extends DomainEvent {

    private String merchantId;
    private BigDecimal amount;

    protected MerchantCreditFailed() {
        super();
    }

    public MerchantCreditFailed(String orderId, String merchantId, BigDecimal amount) {
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
