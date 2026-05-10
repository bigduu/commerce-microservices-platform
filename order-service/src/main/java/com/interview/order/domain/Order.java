package com.interview.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id"),
        @Index(name = "idx_orders_merchant_status", columnList = "merchant_id, status")
})
public class Order {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAYMENT_PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_PROCESSING, Set.of(OrderStatus.INVENTORY_PROCESSING, OrderStatus.FAILED),
            OrderStatus.INVENTORY_PROCESSING, Set.of(OrderStatus.MERCHANT_CREDITING, OrderStatus.FAILED),
            OrderStatus.MERCHANT_CREDITING, Set.of(OrderStatus.COMPLETED, OrderStatus.FAILED),
            OrderStatus.FAILED, Set.of(),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    public Order() {
    }

    public Order(String orderId, String userId, String merchantId, String sku,
                 int quantity, BigDecimal unitPrice) {
        this.orderId = orderId;
        this.userId = userId;
        this.merchantId = merchantId;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.status = OrderStatus.PENDING;
    }

    public void transitionTo(OrderStatus newStatus) {
        if (this.status == newStatus) {
            return;
        }
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(this.status, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid status transition from %s to %s", this.status, newStatus));
        }
        this.status = newStatus;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
