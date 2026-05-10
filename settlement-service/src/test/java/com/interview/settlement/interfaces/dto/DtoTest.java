package com.interview.settlement.interfaces.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DtoTest {

    @Test
    void settlementResponse_defaultConstructorCreatesEmptyObject() {
        SettlementResponse response = new SettlementResponse();

        assertNull(response.getReportId());
        assertNull(response.getMerchantId());
        assertNull(response.getSettlementDate());
        assertNull(response.getExpectedRevenue());
        assertNull(response.getActualRevenue());
        assertNull(response.getStatus());
        assertNull(response.getOrderCount());
        assertNull(response.getCreatedAt());
    }

    @Test
    void settlementResponse_parameterizedConstructorSetsValues() {
        LocalDate settlementDate = LocalDate.of(2024, 1, 15);
        Instant createdAt = Instant.now();

        SettlementResponse response = new SettlementResponse(
                "rpt-1", "merchant-1", settlementDate,
                new BigDecimal("100.00"), new BigDecimal("95.00"),
                "DISCREPANCY", 5, createdAt
        );

        assertEquals("rpt-1", response.getReportId());
        assertEquals("merchant-1", response.getMerchantId());
        assertEquals(settlementDate, response.getSettlementDate());
        assertEquals(new BigDecimal("100.00"), response.getExpectedRevenue());
        assertEquals(new BigDecimal("95.00"), response.getActualRevenue());
        assertEquals("DISCREPANCY", response.getStatus());
        assertEquals(5, response.getOrderCount());
        assertEquals(createdAt, response.getCreatedAt());
    }

    @Test
    void settlementResponse_settersAndGettersWork() {
        SettlementResponse response = new SettlementResponse();
        LocalDate settlementDate = LocalDate.of(2024, 3, 1);
        Instant createdAt = Instant.parse("2024-03-01T10:00:00Z");

        response.setReportId("rpt-2");
        response.setMerchantId("merchant-2");
        response.setSettlementDate(settlementDate);
        response.setExpectedRevenue(new BigDecimal("250.00"));
        response.setActualRevenue(new BigDecimal("250.00"));
        response.setStatus("MATCHED");
        response.setOrderCount(10);
        response.setCreatedAt(createdAt);

        assertEquals("rpt-2", response.getReportId());
        assertEquals("merchant-2", response.getMerchantId());
        assertEquals(settlementDate, response.getSettlementDate());
        assertEquals(new BigDecimal("250.00"), response.getExpectedRevenue());
        assertEquals(new BigDecimal("250.00"), response.getActualRevenue());
        assertEquals("MATCHED", response.getStatus());
        assertEquals(10, response.getOrderCount());
        assertEquals(createdAt, response.getCreatedAt());
    }
}
