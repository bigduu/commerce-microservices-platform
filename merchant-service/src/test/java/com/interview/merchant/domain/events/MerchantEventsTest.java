package com.interview.merchant.domain.events;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MerchantEventsTest {

    @Test
    void inventoryReservedConstructorAndGetters() {
        InventoryReserved event = new InventoryReserved("order-1", "SKU-001", 5);

        assertEquals("order-1", event.getOrderId());
        assertEquals("SKU-001", event.getSku());
        assertEquals(5, event.getQuantity());
        assertEquals("order-1", event.getAggregateId());
        assertEquals("Product", event.getAggregateType());
        assertEquals("InventoryReserved", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void inventoryReservedSettersAndGetters() {
        InventoryReserved event = new InventoryReserved("order-1", "SKU-001", 5);

        event.setOrderId("order-2");
        event.setSku("SKU-002");
        event.setQuantity(10);

        assertEquals("order-2", event.getOrderId());
        assertEquals("SKU-002", event.getSku());
        assertEquals(10, event.getQuantity());
    }

    @Test
    void inventoryReservedDefaultConstructor() {
        InventoryReserved event = new InventoryReserved();

        assertNull(event.getOrderId());
        assertNull(event.getSku());
        assertEquals(0, event.getQuantity());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void inventoryReserveFailedConstructorAndGetters() {
        InventoryReserveFailed event = new InventoryReserveFailed("order-1", "SKU-001", "Not enough stock");

        assertEquals("order-1", event.getOrderId());
        assertEquals("SKU-001", event.getSku());
        assertEquals("Not enough stock", event.getReason());
        assertEquals("order-1", event.getAggregateId());
        assertEquals("Product", event.getAggregateType());
        assertEquals("InventoryReserveFailed", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void inventoryReserveFailedSettersAndGetters() {
        InventoryReserveFailed event = new InventoryReserveFailed("order-1", "SKU-001", "Not enough stock");

        event.setOrderId("order-2");
        event.setSku("SKU-002");
        event.setReason("Product discontinued");

        assertEquals("order-2", event.getOrderId());
        assertEquals("SKU-002", event.getSku());
        assertEquals("Product discontinued", event.getReason());
    }

    @Test
    void inventoryReserveFailedDefaultConstructor() {
        InventoryReserveFailed event = new InventoryReserveFailed();

        assertNull(event.getOrderId());
        assertNull(event.getSku());
        assertNull(event.getReason());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void inventoryReleasedConstructorAndGetters() {
        InventoryReleased event = new InventoryReleased("order-1", "SKU-001", 3);

        assertEquals("order-1", event.getOrderId());
        assertEquals("SKU-001", event.getSku());
        assertEquals(3, event.getQuantity());
        assertEquals("order-1", event.getAggregateId());
        assertEquals("Product", event.getAggregateType());
        assertEquals("InventoryReleased", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void inventoryReleasedSettersAndGetters() {
        InventoryReleased event = new InventoryReleased("order-1", "SKU-001", 3);

        event.setOrderId("order-2");
        event.setSku("SKU-002");
        event.setQuantity(7);

        assertEquals("order-2", event.getOrderId());
        assertEquals("SKU-002", event.getSku());
        assertEquals(7, event.getQuantity());
    }

    @Test
    void inventoryReleasedDefaultConstructor() {
        InventoryReleased event = new InventoryReleased();

        assertNull(event.getOrderId());
        assertNull(event.getSku());
        assertEquals(0, event.getQuantity());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void merchantCreditedConstructorAndGetters() {
        MerchantCredited event = new MerchantCredited("order-1", "merchant-1", new BigDecimal("100.00"));

        assertEquals("order-1", event.getOrderId());
        assertEquals("merchant-1", event.getMerchantId());
        assertEquals(new BigDecimal("100.00"), event.getAmount());
        assertEquals("order-1", event.getAggregateId());
        assertEquals("MerchantAccount", event.getAggregateType());
        assertEquals("MerchantCredited", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void merchantCreditedSettersAndGetters() {
        MerchantCredited event = new MerchantCredited("order-1", "merchant-1", new BigDecimal("100.00"));

        event.setOrderId("order-2");
        event.setMerchantId("merchant-2");
        event.setAmount(new BigDecimal("200.00"));

        assertEquals("order-2", event.getOrderId());
        assertEquals("merchant-2", event.getMerchantId());
        assertEquals(new BigDecimal("200.00"), event.getAmount());
    }

    @Test
    void merchantCreditedDefaultConstructor() {
        MerchantCredited event = new MerchantCredited();

        assertNull(event.getOrderId());
        assertNull(event.getMerchantId());
        assertNull(event.getAmount());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void merchantDebitedConstructorAndGetters() {
        MerchantDebited event = new MerchantDebited("order-1", "merchant-1", new BigDecimal("50.00"));

        assertEquals("order-1", event.getOrderId());
        assertEquals("merchant-1", event.getMerchantId());
        assertEquals(new BigDecimal("50.00"), event.getAmount());
        assertEquals("order-1", event.getAggregateId());
        assertEquals("MerchantAccount", event.getAggregateType());
        assertEquals("MerchantDebited", event.getEventType());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void merchantDebitedSettersAndGetters() {
        MerchantDebited event = new MerchantDebited("order-1", "merchant-1", new BigDecimal("50.00"));

        event.setOrderId("order-2");
        event.setMerchantId("merchant-2");
        event.setAmount(new BigDecimal("75.00"));

        assertEquals("order-2", event.getOrderId());
        assertEquals("merchant-2", event.getMerchantId());
        assertEquals(new BigDecimal("75.00"), event.getAmount());
    }

    @Test
    void merchantDebitedDefaultConstructor() {
        MerchantDebited event = new MerchantDebited();

        assertNull(event.getOrderId());
        assertNull(event.getMerchantId());
        assertNull(event.getAmount());
        assertNotNull(event.getEventId());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void merchantCreditFailedConstructorAndGetters() {
        MerchantCreditFailed event = new MerchantCreditFailed("order-1", "merchant-1", new BigDecimal("100.00"));

        assertEquals("order-1", event.getOrderId());
        assertEquals("merchant-1", event.getMerchantId());
        assertEquals(new BigDecimal("100.00"), event.getAmount());
        assertEquals("MerchantAccount", event.getAggregateType());
        assertEquals("MerchantCreditFailed", event.getEventType());
        assertNotNull(event.getEventId());
    }

    @Test
    void inventoryReleaseFailedConstructorAndGetters() {
        InventoryReleaseFailed event = new InventoryReleaseFailed("order-1", "SKU-001", 3);

        assertEquals("order-1", event.getOrderId());
        assertEquals("SKU-001", event.getSku());
        assertEquals(3, event.getQuantity());
        assertEquals("Product", event.getAggregateType());
        assertEquals("InventoryReleaseFailed", event.getEventType());
        assertNotNull(event.getEventId());
    }
}
