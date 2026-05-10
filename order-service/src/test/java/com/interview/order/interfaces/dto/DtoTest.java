package com.interview.order.interfaces.dto;

import com.interview.order.domain.Order;
import com.interview.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DtoTest {

    @Test
    void createOrderRequest_recordHasCorrectValues() {
        CreateOrderRequest request = new CreateOrderRequest(
                "user-1", "merchant-1", "SKU-001", 2
        );

        assertEquals("user-1", request.userId());
        assertEquals("merchant-1", request.merchantId());
        assertEquals("SKU-001", request.sku());
        assertEquals(2, request.quantity());
    }

    @Test
    void orderResponse_fromOrder_mapsCorrectly() {
        Instant now = Instant.now();
        Order order = new Order("order-1", "user-1", "merchant-1", "SKU-001", 3, new BigDecimal("5.00"));
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        OrderResponse response = OrderResponse.from(order);

        assertEquals("order-1", response.orderId());
        assertEquals("user-1", response.userId());
        assertEquals("merchant-1", response.merchantId());
        assertEquals("SKU-001", response.sku());
        assertEquals(3, response.quantity());
        assertEquals(new BigDecimal("5.00"), response.unitPrice());
        assertEquals(new BigDecimal("15.00"), response.totalAmount());
        assertEquals(OrderStatus.PENDING, response.status());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void orderResponse_recordHasCorrectValues() {
        Instant now = Instant.now();
        OrderResponse response = new OrderResponse(
                "order-2", "user-2", "merchant-2", "SKU-002",
                5, new BigDecimal("2.00"), new BigDecimal("10.00"),
                OrderStatus.COMPLETED, now, now
        );

        assertEquals("order-2", response.orderId());
        assertEquals("user-2", response.userId());
        assertEquals("merchant-2", response.merchantId());
        assertEquals("SKU-002", response.sku());
        assertEquals(5, response.quantity());
        assertEquals(new BigDecimal("2.00"), response.unitPrice());
        assertEquals(new BigDecimal("10.00"), response.totalAmount());
        assertEquals(OrderStatus.COMPLETED, response.status());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }
}
