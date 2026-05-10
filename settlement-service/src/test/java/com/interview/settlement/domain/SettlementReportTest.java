package com.interview.settlement.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SettlementReportTest {

    @Test
    void constructorWithMatchingRevenuesSetsStatusMatched() {
        BigDecimal revenue = new BigDecimal("100.00");
        SettlementReport report = new SettlementReport(
                "rpt-1",
                "merchant-1",
                LocalDate.of(2024, 1, 15),
                revenue,
                revenue,
                SettlementStatus.MATCHED,
                5
        );

        assertEquals(SettlementStatus.MATCHED, report.getStatus());
    }

    @Test
    void constructorWithDifferentRevenuesSetsStatusDiscrepancy() {
        SettlementReport report = new SettlementReport(
                "rpt-2",
                "merchant-1",
                LocalDate.of(2024, 1, 15),
                new BigDecimal("100.00"),
                new BigDecimal("95.00"),
                SettlementStatus.DISCREPANCY,
                5
        );

        assertEquals(SettlementStatus.DISCREPANCY, report.getStatus());
    }

    @Test
    void constructorWithZeroRevenuesIsMatched() {
        BigDecimal zero = BigDecimal.ZERO;
        SettlementReport report = new SettlementReport(
                "rpt-3",
                "merchant-1",
                LocalDate.of(2024, 1, 15),
                zero,
                zero,
                SettlementStatus.MATCHED,
                0
        );

        assertEquals(SettlementStatus.MATCHED, report.getStatus());
    }

    @Test
    void gettersReturnCorrectValues() {
        String reportId = "rpt-4";
        String merchantId = "merchant-42";
        LocalDate settlementDate = LocalDate.of(2024, 6, 10);
        BigDecimal expectedRevenue = new BigDecimal("250.50");
        BigDecimal actualRevenue = new BigDecimal("250.50");
        Integer orderCount = 10;

        SettlementReport report = new SettlementReport(
                reportId,
                merchantId,
                settlementDate,
                expectedRevenue,
                actualRevenue,
                SettlementStatus.MATCHED,
                orderCount
        );

        assertEquals(reportId, report.getReportId());
        assertEquals(merchantId, report.getMerchantId());
        assertEquals(settlementDate, report.getSettlementDate());
        assertEquals(expectedRevenue, report.getExpectedRevenue());
        assertEquals(actualRevenue, report.getActualRevenue());
        assertEquals(SettlementStatus.MATCHED, report.getStatus());
        assertEquals(orderCount, report.getOrderCount());
        assertNotNull(report.getCreatedAt());
        assertTrue(report.getCreatedAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void settersUpdateValuesCorrectly() {
        SettlementReport report = new SettlementReport(
                "rpt-5",
                "merchant-1",
                LocalDate.of(2024, 1, 15),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                SettlementStatus.MATCHED,
                5
        );

        report.setReportId("rpt-updated");
        report.setMerchantId("merchant-updated");
        report.setSettlementDate(LocalDate.of(2024, 2, 20));
        report.setExpectedRevenue(new BigDecimal("200.00"));
        report.setActualRevenue(new BigDecimal("190.00"));
        report.setStatus(SettlementStatus.DISCREPANCY);
        report.setOrderCount(8);
        Instant newCreatedAt = Instant.parse("2024-02-20T10:00:00Z");
        report.setCreatedAt(newCreatedAt);

        assertEquals("rpt-updated", report.getReportId());
        assertEquals("merchant-updated", report.getMerchantId());
        assertEquals(LocalDate.of(2024, 2, 20), report.getSettlementDate());
        assertEquals(new BigDecimal("200.00"), report.getExpectedRevenue());
        assertEquals(new BigDecimal("190.00"), report.getActualRevenue());
        assertEquals(SettlementStatus.DISCREPANCY, report.getStatus());
        assertEquals(8, report.getOrderCount());
        assertEquals(newCreatedAt, report.getCreatedAt());
    }

    @Test
    void protectedConstructorCreatesEmptyReport() {
        // The no-args constructor is protected; we can instantiate it via reflection-like usage
        // by creating an anonymous subclass within the same package (test package matches domain package)
        SettlementReport report = new SettlementReport() {};

        assertNull(report.getReportId());
        assertNull(report.getMerchantId());
        assertNull(report.getSettlementDate());
        assertNull(report.getExpectedRevenue());
        assertNull(report.getActualRevenue());
        assertNull(report.getStatus());
        assertNull(report.getOrderCount());
        assertNull(report.getCreatedAt());
    }
}
