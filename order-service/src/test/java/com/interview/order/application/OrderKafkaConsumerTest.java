package com.interview.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.interview.common.idempotency.ProcessedSagaMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderKafkaConsumerTest {

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    @Mock
    private ProcessedSagaMessageRepository processedSagaMessageRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private OrderKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new OrderKafkaConsumer(sagaOrchestrator, objectMapper, processedSagaMessageRepository);
    }

    private String buildEvent(String eventType, String sagaId, String orderId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventType", eventType,
                "sagaId", sagaId != null ? sagaId : "",
                "orderId", orderId != null ? orderId : "",
                "commandId", "cmd-" + sagaId,
                "eventId", "evt-" + sagaId,
                "aggregateId", "agg-1",
                "aggregateType", "TestAggregate"
        ));
    }

    @Test
    void onEventPaymentDeductedShouldDelegateAndAck() throws Exception {
        String payload = buildEvent("PaymentDeducted", "saga-1", "order-1");

        consumer.onEvent(payload, "user-account.events", "key-1", acknowledgment);

        verify(sagaOrchestrator).handlePaymentDeducted("saga-1", "order-1");
        verify(processedSagaMessageRepository).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventPaymentDeductFailedShouldDelegate() throws Exception {
        String payload = buildEvent("PaymentDeductFailed", "saga-2", "order-2");

        consumer.onEvent(payload, "user-account.events", "key-1", acknowledgment);

        verify(sagaOrchestrator).handlePaymentDeductFailed("saga-2", "order-2");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventInventoryReservedShouldDelegate() throws Exception {
        String payload = buildEvent("InventoryReserved", "saga-3", "order-3");

        consumer.onEvent(payload, "merchant.events", "key-1", acknowledgment);

        verify(sagaOrchestrator).handleInventoryReserved("saga-3", "order-3");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventInventoryReserveFailedShouldDelegate() throws Exception {
        String payload = buildEvent("InventoryReserveFailed", "saga-4", "order-4");

        consumer.onEvent(payload, "merchant.events", "key-1", acknowledgment);

        verify(sagaOrchestrator).handleInventoryReserveFailed("saga-4", "order-4");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventMerchantCreditedShouldDelegate() throws Exception {
        String payload = buildEvent("MerchantCredited", "saga-5", "order-5");

        consumer.onEvent(payload, "merchant.events", "key-1", acknowledgment);

        verify(sagaOrchestrator).handleMerchantCredited("saga-5", "order-5");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventMerchantCreditFailedShouldDelegate() throws Exception {
        String payload = buildEvent("MerchantCreditFailed", "saga-6", "order-6");

        consumer.onEvent(payload, "merchant.events", "key-1", acknowledgment);

        verify(sagaOrchestrator).handleMerchantCreditFailed("saga-6", "order-6");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventUnknownEventTypeShouldNotDelegate() throws Exception {
        String payload = buildEvent("UnknownEvent", "saga-7", "order-7");

        consumer.onEvent(payload, "merchant.events", "key-1", acknowledgment);

        verifyNoInteractions(sagaOrchestrator);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventBlankSagaIdShouldIgnoreAndAck() throws Exception {
        String payload = buildEvent("PaymentDeducted", "", "order-1");

        consumer.onEvent(payload, "user-account.events", "key-1", acknowledgment);

        verifyNoInteractions(sagaOrchestrator);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onEventInvalidJsonShouldNotThrowButNotAck() {
        String payload = "not-valid-json";

        consumer.onEvent(payload, "user-account.events", "key-1", acknowledgment);

        verifyNoInteractions(sagaOrchestrator);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onEventExceptionFromOrchestratorShouldNotThrowButNotAck() throws Exception {
        String payload = buildEvent("PaymentDeducted", "saga-1", "order-1");

        doThrow(new RuntimeException("orchestrator error"))
                .when(sagaOrchestrator).handlePaymentDeducted("saga-1", "order-1");

        consumer.onEvent(payload, "user-account.events", "key-1", acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void onEventDuplicateEventShouldSkipAndAck() throws Exception {
        String payload = buildEvent("PaymentDeducted", "saga-dup", "order-dup");
        when(processedSagaMessageRepository.existsByCommandId("cmd-saga-dup")).thenReturn(true);

        consumer.onEvent(payload, "user-account.events", "key-dup", acknowledgment);

        verifyNoInteractions(sagaOrchestrator);
        verify(processedSagaMessageRepository).existsByCommandId("cmd-saga-dup");
        verify(acknowledgment).acknowledge();
    }
}
