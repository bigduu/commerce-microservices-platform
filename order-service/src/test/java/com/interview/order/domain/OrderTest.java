package com.interview.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderTest {

    private static final String ORDER_ID = "order-1";
    private static final String USER_ID = "user-1";
    private static final String MERCHANT_ID = "merchant-1";
    private static final String SKU = "SKU-001";

    @Test
    void constructorShouldCalculateTotalAmountCorrectly() {
        BigDecimal unitPrice = new BigDecimal("19.99");
        int quantity = 3;

        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, quantity, unitPrice);

        BigDecimal expected = unitPrice.multiply(BigDecimal.valueOf(quantity));
        assertEquals(expected, order.getTotalAmount());
    }

    @Test
    void constructorShouldSetStatusToPending() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);

        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void constructorShouldSetAllFields() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 5, new BigDecimal("7.50"));

        assertEquals(ORDER_ID, order.getOrderId());
        assertEquals(USER_ID, order.getUserId());
        assertEquals(MERCHANT_ID, order.getMerchantId());
        assertEquals(SKU, order.getSku());
        assertEquals(5, order.getQuantity());
        assertEquals(new BigDecimal("7.50"), order.getUnitPrice());
    }

    @Test
    void transitionToShouldAllowPendingToPaymentProcessing() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);

        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);

        assertEquals(OrderStatus.PAYMENT_PROCESSING, order.getStatus());
    }

    @Test
    void transitionToShouldAllowPendingToCancelled() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);

        order.transitionTo(OrderStatus.CANCELLED);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void transitionToShouldAllowPaymentProcessingToInventoryProcessing() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);

        order.transitionTo(OrderStatus.INVENTORY_PROCESSING);

        assertEquals(OrderStatus.INVENTORY_PROCESSING, order.getStatus());
    }

    @Test
    void transitionToShouldAllowPaymentProcessingToFailed() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);

        order.transitionTo(OrderStatus.FAILED);

        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    void transitionToShouldAllowInventoryProcessingToMerchantCrediting() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
        order.transitionTo(OrderStatus.INVENTORY_PROCESSING);

        order.transitionTo(OrderStatus.MERCHANT_CREDITING);

        assertEquals(OrderStatus.MERCHANT_CREDITING, order.getStatus());
    }

    @Test
    void transitionToShouldAllowInventoryProcessingToFailed() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
        order.transitionTo(OrderStatus.INVENTORY_PROCESSING);

        order.transitionTo(OrderStatus.FAILED);

        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    void transitionToShouldAllowMerchantCreditingToCompleted() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
        order.transitionTo(OrderStatus.INVENTORY_PROCESSING);
        order.transitionTo(OrderStatus.MERCHANT_CREDITING);

        order.transitionTo(OrderStatus.COMPLETED);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void transitionToShouldAllowMerchantCreditingToFailed() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
        order.transitionTo(OrderStatus.INVENTORY_PROCESSING);
        order.transitionTo(OrderStatus.MERCHANT_CREDITING);

        order.transitionTo(OrderStatus.FAILED);

        assertEquals(OrderStatus.FAILED, order.getStatus());
    }

    @Test
    void transitionToShouldCompleteFullHappyPath() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);

        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
        order.transitionTo(OrderStatus.INVENTORY_PROCESSING);
        order.transitionTo(OrderStatus.MERCHANT_CREDITING);
        order.transitionTo(OrderStatus.COMPLETED);

        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    @Test
    void transitionToSameStatusShouldBeNoOp() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);

        order.transitionTo(OrderStatus.PENDING);

        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,INVENTORY_PROCESSING",
            "PENDING,MERCHANT_CREDITING",
            "PENDING,COMPLETED",
            "PENDING,FAILED",
            "PAYMENT_PROCESSING,PENDING",
            "PAYMENT_PROCESSING,MERCHANT_CREDITING",
            "PAYMENT_PROCESSING,COMPLETED",
            "PAYMENT_PROCESSING,CANCELLED",
            "INVENTORY_PROCESSING,PENDING",
            "INVENTORY_PROCESSING,PAYMENT_PROCESSING",
            "INVENTORY_PROCESSING,COMPLETED",
            "INVENTORY_PROCESSING,CANCELLED",
            "MERCHANT_CREDITING,PENDING",
            "MERCHANT_CREDITING,PAYMENT_PROCESSING",
            "MERCHANT_CREDITING,INVENTORY_PROCESSING",
            "MERCHANT_CREDITING,CANCELLED",
            "FAILED,PENDING",
            "FAILED,PAYMENT_PROCESSING",
            "FAILED,INVENTORY_PROCESSING",
            "FAILED,MERCHANT_CREDITING",
            "FAILED,COMPLETED",
            "FAILED,CANCELLED",
            "COMPLETED,PENDING",
            "COMPLETED,PAYMENT_PROCESSING",
            "COMPLETED,INVENTORY_PROCESSING",
            "COMPLETED,MERCHANT_CREDITING",
            "COMPLETED,FAILED",
            "COMPLETED,CANCELLED",
            "CANCELLED,PENDING",
            "CANCELLED,PAYMENT_PROCESSING",
            "CANCELLED,INVENTORY_PROCESSING",
            "CANCELLED,MERCHANT_CREDITING",
            "CANCELLED,COMPLETED",
            "CANCELLED,FAILED"
    })
    void transitionToInvalidTransitionShouldThrowIllegalStateException(String fromStatus, String toStatus) {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.setStatus(OrderStatus.valueOf(fromStatus));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> order.transitionTo(OrderStatus.valueOf(toStatus)));

        assertEquals(
                String.format("Invalid status transition from %s to %s", fromStatus, toStatus),
                exception.getMessage()
        );
    }

    @Test
    void transitionToShouldThrowWhenTransitioningFromFailed() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.setStatus(OrderStatus.FAILED);

        assertThrows(IllegalStateException.class,
                () -> order.transitionTo(OrderStatus.PENDING));
    }

    @Test
    void transitionToShouldThrowWhenTransitioningFromCompleted() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.setStatus(OrderStatus.COMPLETED);

        assertThrows(IllegalStateException.class,
                () -> order.transitionTo(OrderStatus.FAILED));
    }

    @Test
    void transitionToShouldThrowWhenTransitioningFromCancelled() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        order.setStatus(OrderStatus.CANCELLED);

        assertThrows(IllegalStateException.class,
                () -> order.transitionTo(OrderStatus.PENDING));
    }

    @Test
    void settersAndGettersShouldWork() {
        Order order = new Order();
        order.setOrderId("o1");
        order.setUserId("u1");
        order.setMerchantId("m1");
        order.setSku("sku");
        order.setQuantity(2);
        order.setUnitPrice(BigDecimal.ONE);
        order.setTotalAmount(new BigDecimal("2.00"));
        order.setStatus(OrderStatus.PENDING);

        assertEquals("o1", order.getOrderId());
        assertEquals("u1", order.getUserId());
        assertEquals("m1", order.getMerchantId());
        assertEquals("sku", order.getSku());
        assertEquals(2, order.getQuantity());
        assertEquals(BigDecimal.ONE, order.getUnitPrice());
        assertEquals(new BigDecimal("2.00"), order.getTotalAmount());
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }
}
