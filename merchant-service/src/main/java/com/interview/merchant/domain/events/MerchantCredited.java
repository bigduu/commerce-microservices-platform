package com.interview.merchant.domain.events;

import com.interview.common.event.DomainEvent;

import java.math.BigDecimal;

public class MerchantCredited extends DomainEvent {

    private String orderId;
    private String merchantId;
    private BigDecimal amount;

    protected MerchantCredited() {
        super();
    }

    public MerchantCredited(String orderId, String merchantId, BigDecimal amount) {
        super(orderId, "MerchantAccount");
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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
