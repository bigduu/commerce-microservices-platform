package com.interview.order.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.idempotency.ProcessedSagaMessage;
import com.interview.common.idempotency.ProcessedSagaMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class OrderKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OrderKafkaConsumer.class);

    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;
    private final ProcessedSagaMessageRepository processedSagaMessageRepository;

    public OrderKafkaConsumer(SagaOrchestrator sagaOrchestrator,
                             ObjectMapper objectMapper,
                             ProcessedSagaMessageRepository processedSagaMessageRepository) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
        this.processedSagaMessageRepository = processedSagaMessageRepository;
    }

    @KafkaListener(topics = {
            "user-account.events",
            "merchant.events"
    }, groupId = "order-service")
    public void onEvent(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        Acknowledgment acknowledgment) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("eventType").asText();
            String sagaId = root.path("sagaId").asText();
            String orderId = root.path("orderId").asText();
            String commandId = root.path("commandId").asText();
            String eventId = root.path("eventId").asText();

            if (sagaId == null || sagaId.isBlank()) {
                logger.warn("Received event without sagaId, ignoring");
                acknowledgment.acknowledge();
                return;
            }

            String dedupKey = commandId != null && !commandId.isBlank() ? commandId : eventId;
            if (dedupKey != null && !dedupKey.isBlank()
                    && processedSagaMessageRepository.existsByCommandId(dedupKey)) {
                logger.info("Event {} already processed by order consumer, skipping", eventType);
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Received event {} from topic {} for saga {}", eventType, topic, sagaId);

            switch (eventType) {
                case "PaymentDeducted" -> sagaOrchestrator.handlePaymentDeducted(sagaId, orderId);
                case "PaymentDeductFailed" -> sagaOrchestrator.handlePaymentDeductFailed(sagaId, orderId);
                case "InventoryReserved" -> sagaOrchestrator.handleInventoryReserved(sagaId, orderId);
                case "InventoryReserveFailed" -> sagaOrchestrator.handleInventoryReserveFailed(sagaId, orderId);
                case "MerchantCredited" -> sagaOrchestrator.handleMerchantCredited(sagaId, orderId);
                case "MerchantCreditFailed" -> sagaOrchestrator.handleMerchantCreditFailed(sagaId, orderId);
                default -> logger.warn("Unknown event type: {}", eventType);
            }

            if (dedupKey != null && !dedupKey.isBlank()) {
                processedSagaMessageRepository.save(
                        new ProcessedSagaMessage(dedupKey, sagaId, orderId, "order-consumer"));
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Failed to process event from topic {}", topic, e);
        }
    }
}
