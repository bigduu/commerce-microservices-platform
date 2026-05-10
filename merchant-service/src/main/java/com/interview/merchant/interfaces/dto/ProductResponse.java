package com.interview.merchant.interfaces.dto;

import java.math.BigDecimal;

public class ProductResponse {

    private String sku;
    private String merchantId;
    private String name;
    private BigDecimal price;
    private Integer quantity;

    public ProductResponse(String sku, String merchantId, String name, BigDecimal price, Integer quantity) {
        this.sku = sku;
        this.merchantId = merchantId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public String getSku() {
        return sku;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
