package com.interview.merchant.domain.events;

import com.interview.common.event.DomainEvent;

public class InventoryReserveFailed extends DomainEvent {

    private String orderId;
    private String sku;
    private String reason;

    protected InventoryReserveFailed() {
        super();
    }

    public InventoryReserveFailed(String orderId, String sku, String reason) {
        super(orderId, "Product");
        this.orderId = orderId;
        this.sku = sku;
        this.reason = reason;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
