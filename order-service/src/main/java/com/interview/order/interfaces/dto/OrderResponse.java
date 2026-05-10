package com.interview.order.interfaces.dto;

import com.interview.order.domain.Order;
import com.interview.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        String orderId,
        String userId,
        String merchantId,
        String sku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getUserId(),
                order.getMerchantId(),
                order.getSku(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
