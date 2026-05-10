package com.interview.merchant.interfaces.dto;

import jakarta.validation.constraints.Positive;

public class AddInventoryRequest {

    @Positive
    private int quantity;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
