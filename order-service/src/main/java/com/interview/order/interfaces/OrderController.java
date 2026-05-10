package com.interview.order.interfaces;

import com.interview.order.application.OrderService;
import com.interview.order.domain.Order;
import com.interview.order.interfaces.dto.CreateOrderRequest;
import com.interview.order.interfaces.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                request.userId(),
                request.merchantId(),
                request.sku(),
                request.quantity()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable String userId) {
        List<Order> orders = orderService.getOrdersByUser(userId);
        List<OrderResponse> responses = orders.stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
