package com.interview.settlement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.settlement.domain.MerchantCreditReadModel;
import com.interview.settlement.domain.MerchantCreditReadModelRepository;
import com.interview.settlement.domain.OrderReadModel;
import com.interview.settlement.domain.OrderReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementKafkaConsumerTest {

    @Mock
    private OrderReadModelRepository orderReadModelRepository;

    @Mock
    private MerchantCreditReadModelRepository merchantCreditReadModelRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private SettlementKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new SettlementKafkaConsumer(orderReadModelRepository, merchantCreditReadModelRepository, objectMapper);
    }

    @Test
    void handleOrderEvent_orderCompleted_savesOrderReadModel() throws Exception {
        Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "OrderCompleted",
                "aggregateId", "order-1",
                "merchantId", "merchant-1",
                "userId", "user-1",
                "sku", "SKU-001",
                "quantity", 5,
                "unitPrice", "29.99",
                "totalAmount", "149.95",
                "occurredAt", occurredAt.toString()
        ));

        when(orderReadModelRepository.existsByOrderId("order-1")).thenReturn(false);

        consumer.handleOrderEvent(message, acknowledgment);

        verify(orderReadModelRepository).existsByOrderId("order-1");
        ArgumentCaptor<OrderReadModel> captor = ArgumentCaptor.forClass(OrderReadModel.class);
        verify(orderReadModelRepository).save(captor.capture());
        OrderReadModel saved = captor.getValue();
        assertEquals("order-1", saved.getOrderId());
        assertEquals("merchant-1", saved.getMerchantId());
        assertEquals("user-1", saved.getUserId());
        assertEquals("SKU-001", saved.getSku());
        assertEquals(5, saved.getQuantity());
        assertEquals(new BigDecimal("29.99"), saved.getUnitPrice());
        assertEquals(new BigDecimal("149.95"), saved.getTotalAmount());
        assertEquals(occurredAt, saved.getCompletedAt());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleOrderEvent_orderCompleted_alreadyProcessed_skips() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "OrderCompleted",
                "aggregateId", "order-1"
        ));

        when(orderReadModelRepository.existsByOrderId("order-1")).thenReturn(true);

        consumer.handleOrderEvent(message, acknowledgment);

        verify(orderReadModelRepository).existsByOrderId("order-1");
        verify(orderReadModelRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleOrderEvent_otherEventType_acknowledgesOnly() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "OrderCreated",
                "aggregateId", "order-1"
        ));

        consumer.handleOrderEvent(message, acknowledgment);

        verifyNoInteractions(orderReadModelRepository);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleOrderEvent_missingOptionalFields_usesDefaults() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "OrderCompleted",
                "aggregateId", "order-1"
        ));

        when(orderReadModelRepository.existsByOrderId("order-1")).thenReturn(false);

        consumer.handleOrderEvent(message, acknowledgment);

        ArgumentCaptor<OrderReadModel> captor = ArgumentCaptor.forClass(OrderReadModel.class);
        verify(orderReadModelRepository).save(captor.capture());
        OrderReadModel saved = captor.getValue();
        assertEquals("order-1", saved.getOrderId());
        assertNull(saved.getMerchantId());
        assertNull(saved.getUserId());
        assertNull(saved.getSku());
        assertEquals(0, saved.getQuantity());
        assertEquals(BigDecimal.ZERO, saved.getUnitPrice());
        assertEquals(BigDecimal.ZERO, saved.getTotalAmount());
        assertNotNull(saved.getCompletedAt());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleOrderEvent_invalidJson_doesNotAcknowledge() {
        String message = "invalid json";

        consumer.handleOrderEvent(message, acknowledgment);

        verifyNoInteractions(orderReadModelRepository);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleMerchantEvent_merchantCredited_savesCreditReadModel() throws Exception {
        Instant occurredAt = Instant.parse("2024-01-15T10:00:00Z");
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "MerchantCredited",
                "eventId", "event-1",
                "orderId", "order-1",
                "merchantId", "merchant-1",
                "amount", "100.00",
                "occurredAt", occurredAt.toString()
        ));

        when(merchantCreditReadModelRepository.existsByEventId("event-1")).thenReturn(false);

        consumer.handleMerchantEvent(message, acknowledgment);

        verify(merchantCreditReadModelRepository).existsByEventId("event-1");
        ArgumentCaptor<MerchantCreditReadModel> captor = ArgumentCaptor.forClass(MerchantCreditReadModel.class);
        verify(merchantCreditReadModelRepository).save(captor.capture());
        MerchantCreditReadModel saved = captor.getValue();
        assertEquals("event-1", saved.getEventId());
        assertEquals("order-1", saved.getOrderId());
        assertEquals("merchant-1", saved.getMerchantId());
        assertEquals(new BigDecimal("100.00"), saved.getAmount());
        assertEquals(occurredAt, saved.getCreditedAt());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleMerchantEvent_merchantCredited_alreadyProcessed_skips() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "MerchantCredited",
                "eventId", "event-1"
        ));

        when(merchantCreditReadModelRepository.existsByEventId("event-1")).thenReturn(true);

        consumer.handleMerchantEvent(message, acknowledgment);

        verify(merchantCreditReadModelRepository).existsByEventId("event-1");
        verify(merchantCreditReadModelRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleMerchantEvent_merchantCredited_missingEventId_generatesOne() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "MerchantCredited",
                "aggregateId", "order-1",
                "orderId", "order-1",
                "merchantId", "merchant-1",
                "amount", "100.00"
        ));

        consumer.handleMerchantEvent(message, acknowledgment);

        ArgumentCaptor<MerchantCreditReadModel> captor = ArgumentCaptor.forClass(MerchantCreditReadModel.class);
        verify(merchantCreditReadModelRepository).save(captor.capture());
        MerchantCreditReadModel saved = captor.getValue();
        assertNotNull(saved.getEventId());
        assertTrue(saved.getEventId().startsWith("order-1-"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleMerchantEvent_otherEventType_acknowledgesOnly() throws Exception {
        String message = objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "MerchantDebited",
                "aggregateId", "merchant-1"
        ));

        consumer.handleMerchantEvent(message, acknowledgment);

        verifyNoInteractions(merchantCreditReadModelRepository);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleMerchantEvent_invalidJson_doesNotAcknowledge() {
        String message = "invalid json";

        consumer.handleMerchantEvent(message, acknowledgment);

        verifyNoInteractions(merchantCreditReadModelRepository);
        verify(acknowledgment, never()).acknowledge();
    }
}
