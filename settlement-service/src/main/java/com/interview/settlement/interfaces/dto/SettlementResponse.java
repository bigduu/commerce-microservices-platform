package com.interview.settlement.interfaces.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class SettlementResponse {

    private String reportId;
    private String merchantId;
    private LocalDate settlementDate;
    private BigDecimal expectedRevenue;
    private BigDecimal actualRevenue;
    private String status;
    private Integer orderCount;
    private Instant createdAt;

    public SettlementResponse() {
    }

    public SettlementResponse(String reportId, String merchantId, LocalDate settlementDate,
                              BigDecimal expectedRevenue, BigDecimal actualRevenue,
                              String status, Integer orderCount, Instant createdAt) {
        this.reportId = reportId;
        this.merchantId = merchantId;
        this.settlementDate = settlementDate;
        this.expectedRevenue = expectedRevenue;
        this.actualRevenue = actualRevenue;
        this.status = status;
        this.orderCount = orderCount;
        this.createdAt = createdAt;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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
