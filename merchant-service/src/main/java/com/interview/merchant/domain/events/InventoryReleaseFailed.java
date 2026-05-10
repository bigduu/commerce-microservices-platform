package com.interview.merchant.domain.events;

import com.interview.common.event.DomainEvent;

public class InventoryReleaseFailed extends DomainEvent {

    private String sku;
    private int quantity;

    protected InventoryReleaseFailed() {
        super();
    }

    public InventoryReleaseFailed(String orderId, String sku, int quantity) {
        super(orderId, "Product");
        this.sku = sku;
        this.quantity = quantity;
        setOrderId(orderId);
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
