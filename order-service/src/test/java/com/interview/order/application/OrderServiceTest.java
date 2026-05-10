package com.interview.order.application;

import com.interview.order.domain.Order;
import com.interview.order.domain.OrderRepository;
import com.interview.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    @Mock
    private MerchantProductClient merchantProductClient;

    @InjectMocks
    private OrderService orderService;

    private static final String USER_ID = "user-1";
    private static final String MERCHANT_ID = "merchant-1";
    private static final String SKU = "SKU-001";
    private static final int QUANTITY = 2;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("10.00");

    @Test
    void createOrder_shouldCreateOrder_andStartSaga() {
        when(merchantProductClient.getUnitPrice(MERCHANT_ID, SKU)).thenReturn(UNIT_PRICE);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createOrder(USER_ID, MERCHANT_ID, SKU, QUANTITY);

        assertNotNull(result);
        assertNotNull(result.getOrderId());
        assertTrue(UUID.fromString(result.getOrderId()) instanceof UUID);
        assertEquals(USER_ID, result.getUserId());
        assertEquals(MERCHANT_ID, result.getMerchantId());
        assertEquals(SKU, result.getSku());
        assertEquals(QUANTITY, result.getQuantity());
        assertEquals(UNIT_PRICE, result.getUnitPrice());
        assertEquals(new BigDecimal("20.00"), result.getTotalAmount());
        assertEquals(OrderStatus.PENDING, result.getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());

        verify(merchantProductClient).getUnitPrice(MERCHANT_ID, SKU);
        verify(sagaOrchestrator).startSaga(savedOrder);
    }

    @Test
    void createOrder_shouldThrow_whenQuantityIsZero() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(USER_ID, MERCHANT_ID, SKU, 0));
        assertEquals("Quantity must be positive", exception.getMessage());
    }

    @Test
    void createOrder_shouldThrow_whenQuantityIsNegative() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(USER_ID, MERCHANT_ID, SKU, -1));
        assertEquals("Quantity must be positive", exception.getMessage());
    }

    @Test
    void createOrder_shouldThrow_whenResolvedUnitPriceIsNull() {
        when(merchantProductClient.getUnitPrice(MERCHANT_ID, SKU)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(USER_ID, MERCHANT_ID, SKU, QUANTITY));
        assertEquals("Resolved unit price must be positive", exception.getMessage());
    }

    @Test
    void createOrder_shouldThrow_whenResolvedUnitPriceIsZero() {
        when(merchantProductClient.getUnitPrice(MERCHANT_ID, SKU)).thenReturn(BigDecimal.ZERO);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(USER_ID, MERCHANT_ID, SKU, QUANTITY));
        assertEquals("Resolved unit price must be positive", exception.getMessage());
    }

    @Test
    void createOrder_shouldThrow_whenResolvedUnitPriceIsNegative() {
        when(merchantProductClient.getUnitPrice(MERCHANT_ID, SKU)).thenReturn(new BigDecimal("-1.00"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(USER_ID, MERCHANT_ID, SKU, QUANTITY));
        assertEquals("Resolved unit price must be positive", exception.getMessage());
    }

    @Test
    void getOrder_shouldReturnOrder_whenFound() {
        String orderId = "order-1";
        Order order = new Order(orderId, USER_ID, MERCHANT_ID, SKU, QUANTITY, UNIT_PRICE);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Order result = orderService.getOrder(orderId);

        assertEquals(orderId, result.getOrderId());
        assertEquals(USER_ID, result.getUserId());
        verify(orderRepository).findById(orderId);
    }

    @Test
    void getOrder_shouldThrow_whenNotFound() {
        String orderId = "order-not-found";
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> orderService.getOrder(orderId));
        assertEquals("Order not found: " + orderId, exception.getMessage());
    }

    @Test
    void getOrdersByUser_shouldReturnListOfOrders() {
        Order order1 = new Order("order-1", USER_ID, MERCHANT_ID, SKU, 1, BigDecimal.TEN);
        Order order2 = new Order("order-2", USER_ID, MERCHANT_ID, "SKU-002", 2, new BigDecimal("5.00"));
        List<Order> expectedOrders = List.of(order1, order2);

        when(orderRepository.findByUserId(USER_ID)).thenReturn(expectedOrders);

        List<Order> result = orderService.getOrdersByUser(USER_ID);

        assertEquals(2, result.size());
        assertEquals("order-1", result.get(0).getOrderId());
        assertEquals("order-2", result.get(1).getOrderId());
        verify(orderRepository).findByUserId(USER_ID);
    }

    @Test
    void getOrdersByUser_shouldReturnEmptyList_whenNoOrders() {
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of());

        List<Order> result = orderService.getOrdersByUser(USER_ID);

        assertTrue(result.isEmpty());
        verify(orderRepository).findByUserId(USER_ID);
    }

    @Test
    void createOrder_shouldGenerateUniqueOrderIds() {
        when(merchantProductClient.getUnitPrice(MERCHANT_ID, SKU)).thenReturn(BigDecimal.TEN);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order1 = orderService.createOrder(USER_ID, MERCHANT_ID, SKU, 1);
        Order order2 = orderService.createOrder(USER_ID, MERCHANT_ID, SKU, 1);

        assertNotNull(order1.getOrderId());
        assertNotNull(order2.getOrderId());
        assertTrue(UUID.fromString(order1.getOrderId()) instanceof UUID);
        assertTrue(UUID.fromString(order2.getOrderId()) instanceof UUID);
        assert !order1.getOrderId().equals(order2.getOrderId());

        verify(merchantProductClient, times(2)).getUnitPrice(MERCHANT_ID, SKU);
        verify(sagaOrchestrator, times(2)).startSaga(any(Order.class));
    }
}
