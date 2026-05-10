package com.interview.settlement.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReadModelTest {

    @Test
    void orderReadModel_parameterizedConstructorSetsValues() {
        Instant completedAt = Instant.parse("2024-01-15T10:00:00Z");

        OrderReadModel order = new OrderReadModel(
                "order-1", "merchant-1", "user-1",
                "SKU-001", 5,
                new BigDecimal("29.99"), new BigDecimal("149.95"),
                completedAt
        );

        assertNull(order.getId());
        assertEquals("order-1", order.getOrderId());
        assertEquals("merchant-1", order.getMerchantId());
        assertEquals("user-1", order.getUserId());
        assertEquals("SKU-001", order.getSku());
        assertEquals(5, order.getQuantity());
        assertEquals(new BigDecimal("29.99"), order.getUnitPrice());
        assertEquals(new BigDecimal("149.95"), order.getTotalAmount());
        assertEquals(completedAt, order.getCompletedAt());
    }

    @Test
    void orderReadModel_settersAndGettersWork() {
        OrderReadModel order = new OrderReadModel();
        Instant completedAt = Instant.parse("2024-03-01T12:00:00Z");

        order.setId(1L);
        order.setOrderId("order-2");
        order.setMerchantId("merchant-2");
        order.setUserId("user-2");
        order.setSku("SKU-002");
        order.setQuantity(10);
        order.setUnitPrice(new BigDecimal("19.99"));
        order.setTotalAmount(new BigDecimal("199.90"));
        order.setCompletedAt(completedAt);

        assertEquals(1L, order.getId());
        assertEquals("order-2", order.getOrderId());
        assertEquals("merchant-2", order.getMerchantId());
        assertEquals("user-2", order.getUserId());
        assertEquals("SKU-002", order.getSku());
        assertEquals(10, order.getQuantity());
        assertEquals(new BigDecimal("19.99"), order.getUnitPrice());
        assertEquals(new BigDecimal("199.90"), order.getTotalAmount());
        assertEquals(completedAt, order.getCompletedAt());
    }

    @Test
    void orderReadModel_defaultConstructorCreatesEmptyObject() {
        OrderReadModel order = new OrderReadModel();

        assertNull(order.getId());
        assertNull(order.getOrderId());
        assertNull(order.getMerchantId());
        assertNull(order.getUserId());
        assertNull(order.getSku());
        assertNull(order.getQuantity());
        assertNull(order.getUnitPrice());
        assertNull(order.getTotalAmount());
        assertNull(order.getCompletedAt());
    }

    @Test
    void merchantCreditReadModel_parameterizedConstructorSetsValues() {
        Instant creditedAt = Instant.parse("2024-01-15T10:00:00Z");

        MerchantCreditReadModel credit = new MerchantCreditReadModel(
                "event-1", "order-1", "merchant-1",
                new BigDecimal("100.00"), creditedAt
        );

        assertNull(credit.getId());
        assertEquals("event-1", credit.getEventId());
        assertEquals("order-1", credit.getOrderId());
        assertEquals("merchant-1", credit.getMerchantId());
        assertEquals(new BigDecimal("100.00"), credit.getAmount());
        assertEquals(creditedAt, credit.getCreditedAt());
    }

    @Test
    void merchantCreditReadModel_settersAndGettersWork() {
        MerchantCreditReadModel credit = new MerchantCreditReadModel();
        Instant creditedAt = Instant.parse("2024-03-01T12:00:00Z");

        credit.setId(1L);
        credit.setEventId("event-2");
        credit.setOrderId("order-2");
        credit.setMerchantId("merchant-2");
        credit.setAmount(new BigDecimal("250.00"));
        credit.setCreditedAt(creditedAt);

        assertEquals(1L, credit.getId());
        assertEquals("event-2", credit.getEventId());
        assertEquals("order-2", credit.getOrderId());
        assertEquals("merchant-2", credit.getMerchantId());
        assertEquals(new BigDecimal("250.00"), credit.getAmount());
        assertEquals(creditedAt, credit.getCreditedAt());
    }

    @Test
    void merchantCreditReadModel_defaultConstructorCreatesEmptyObject() {
        MerchantCreditReadModel credit = new MerchantCreditReadModel();

        assertNull(credit.getId());
        assertNull(credit.getEventId());
        assertNull(credit.getOrderId());
        assertNull(credit.getMerchantId());
        assertNull(credit.getAmount());
        assertNull(credit.getCreditedAt());
    }
}
