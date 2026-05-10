package com.interview.merchant.domain;

import com.interview.common.exception.InsufficientInventoryException;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String sku;

    private String merchantId;

    private String name;

    private BigDecimal price;

    private Integer quantity;

    @Version
    private Long version;

    protected Product() {}

    private Product(String sku, String merchantId, String name, BigDecimal price, int quantity) {
        this.sku = sku;
        this.merchantId = merchantId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public static Product create(String sku, String merchantId, String name, BigDecimal price, int quantity) {
        return new Product(sku, merchantId, name, price, quantity);
    }

    public void addInventory(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity += qty;
    }

    public void deductInventory(int qty) {
        if (qty > this.quantity) {
            throw new InsufficientInventoryException("Insufficient inventory for SKU: " + sku);
        }
        this.quantity -= qty;
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

    public Long getVersion() {
        return version;
    }
}
