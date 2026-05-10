package com.interview.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "settlement_reports",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_merchant_settlement_date",
        columnNames = {"merchant_id", "settlement_date"}
    )
)
public class SettlementReport {

    @Id
    @Column(name = "report_id")
    private String reportId;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "expected_revenue", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedRevenue;

    @Column(name = "actual_revenue", nullable = false, precision = 19, scale = 4)
    private BigDecimal actualRevenue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementStatus status;

    @Column(name = "order_count", nullable = false)
    private Integer orderCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SettlementReport() {
    }

    public SettlementReport(String reportId, String merchantId, LocalDate settlementDate,
                            BigDecimal expectedRevenue, BigDecimal actualRevenue,
                            SettlementStatus status, Integer orderCount) {
        this.reportId = reportId;
        this.merchantId = merchantId;
        this.settlementDate = settlementDate;
        this.expectedRevenue = expectedRevenue;
        this.actualRevenue = actualRevenue;
        this.status = status;
        this.orderCount = orderCount;
        this.createdAt = Instant.now();
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public void setSettlementDate(LocalDate settlementDate) {
        this.settlementDate = settlementDate;
    }

    public BigDecimal getExpectedRevenue() {
        return expectedRevenue;
    }

    public void setExpectedRevenue(BigDecimal expectedRevenue) {
        this.expectedRevenue = expectedRevenue;
    }

    public BigDecimal getActualRevenue() {
        return actualRevenue;
    }

    public void setActualRevenue(BigDecimal actualRevenue) {
        this.actualRevenue = actualRevenue;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
