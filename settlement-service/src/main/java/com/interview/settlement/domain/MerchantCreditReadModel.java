package com.interview.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "merchant_credit_read_models",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_event_id",
        columnNames = {"event_id"}
    ),
    indexes = {
        @Index(name = "idx_mcrm_merchant_credited", columnList = "merchant_id, credited_at")
    }
)
public class MerchantCreditReadModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "credited_at", nullable = false)
    private Instant creditedAt;

    protected MerchantCreditReadModel() {
    }

    public MerchantCreditReadModel(String eventId, String orderId, String merchantId,
                                   BigDecimal amount, Instant creditedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.creditedAt = creditedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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

    public Instant getCreditedAt() {
        return creditedAt;
    }

    public void setCreditedAt(Instant creditedAt) {
        this.creditedAt = creditedAt;
    }
}
