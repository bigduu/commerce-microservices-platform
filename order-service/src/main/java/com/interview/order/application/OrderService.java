package com.interview.order.application;

import com.interview.order.domain.Order;
import com.interview.order.domain.OrderRepository;
import com.interview.order.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final MerchantProductClient merchantProductClient;

    public OrderService(OrderRepository orderRepository,
                        SagaOrchestrator sagaOrchestrator,
                        MerchantProductClient merchantProductClient) {
        this.orderRepository = orderRepository;
        this.sagaOrchestrator = sagaOrchestrator;
        this.merchantProductClient = merchantProductClient;
    }

    public Order createOrder(String userId, String merchantId, String sku,
                             int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        BigDecimal unitPrice = merchantProductClient.getUnitPrice(merchantId, sku);
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Resolved unit price must be positive");
        }

        String orderId = UUID.randomUUID().toString();
        Order order = new Order(orderId, userId, merchantId, sku, quantity, unitPrice);
        order.setStatus(OrderStatus.PENDING);

        orderRepository.save(order);
        sagaOrchestrator.startSaga(order);

        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId);
    }
}
