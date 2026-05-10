package com.interview.merchant.domain.events;

import com.interview.common.event.DomainEvent;

public class InventoryReleased extends DomainEvent {

    private String orderId;
    private String sku;
    private int quantity;

    protected InventoryReleased() {
        super();
    }

    public InventoryReleased(String orderId, String sku, int quantity) {
        super(orderId, "Product");
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
