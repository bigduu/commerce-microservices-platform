package com.interview.order.interfaces;

import com.interview.order.application.OrderService;
import com.interview.order.domain.Order;
import com.interview.order.domain.OrderStatus;
import com.interview.order.interfaces.dto.CreateOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void createOrder_returns201() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "user-1", "merchant-1", "SKU-001", 2
        );

        Order order = createOrder("order-1", "user-1", "merchant-1", "SKU-001", 2, new BigDecimal("10.00"));

        when(orderService.createOrder(
                eq("user-1"), eq("merchant-1"), eq("SKU-001"), eq(2)
        )).thenReturn(order);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.merchantId").value("merchant-1"))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(10.00))
                .andExpect(jsonPath("$.totalAmount").value(20.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrder_returns200() throws Exception {
        Order order = createOrder("order-1", "user-1", "merchant-1", "SKU-001", 2, new BigDecimal("10.00"));

        when(orderService.getOrder("order-1")).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.merchantId").value("merchant-1"))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getUserOrders_returns200WithList() throws Exception {
        Order order1 = createOrder("order-1", "user-1", "merchant-1", "SKU-001", 1, new BigDecimal("5.00"));
        Order order2 = createOrder("order-2", "user-1", "merchant-2", "SKU-002", 3, new BigDecimal("7.50"));

        when(orderService.getOrdersByUser("user-1")).thenReturn(List.of(order1, order2));

        mockMvc.perform(get("/api/v1/users/user-1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value("order-1"))
                .andExpect(jsonPath("$[0].userId").value("user-1"))
                .andExpect(jsonPath("$[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$[1].orderId").value("order-2"))
                .andExpect(jsonPath("$[1].sku").value("SKU-002"))
                .andExpect(jsonPath("$[1].quantity").value(3));
    }

    @Test
    void createOrder_withInvalidRequest_returns400() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "", "merchant-1", "SKU-001", 0
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_whenNotFound_returns400() throws Exception {
        when(orderService.getOrder("not-found"))
                .thenThrow(new IllegalArgumentException("Order not found: not-found"));

        mockMvc.perform(get("/api/v1/orders/not-found"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Order not found: not-found"));
    }

    private Order createOrder(String orderId, String userId, String merchantId,
                              String sku, int quantity, BigDecimal unitPrice) {
        Order order = new Order(orderId, userId, merchantId, sku, quantity, unitPrice);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }
}
