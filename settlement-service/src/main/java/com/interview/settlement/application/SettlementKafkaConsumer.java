package com.interview.settlement.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.settlement.domain.MerchantCreditReadModel;
import com.interview.settlement.domain.MerchantCreditReadModelRepository;
import com.interview.settlement.domain.OrderReadModel;
import com.interview.settlement.domain.OrderReadModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class SettlementKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SettlementKafkaConsumer.class);

    private final OrderReadModelRepository orderReadModelRepository;
    private final MerchantCreditReadModelRepository merchantCreditReadModelRepository;
    private final ObjectMapper objectMapper;

    public SettlementKafkaConsumer(OrderReadModelRepository orderReadModelRepository,
                                   MerchantCreditReadModelRepository merchantCreditReadModelRepository,
                                   ObjectMapper objectMapper) {
        this.orderReadModelRepository = orderReadModelRepository;
        this.merchantCreditReadModelRepository = merchantCreditReadModelRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleOrderEvent(String message, Acknowledgment acknowledgment) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String eventType = json.get("eventType").asText();

            if ("OrderCompleted".equals(eventType)) {
                String orderId = json.get("aggregateId").asText();

                // Idempotency check
                if (orderReadModelRepository.existsByOrderId(orderId)) {
                    logger.info("Order {} already processed, skipping", orderId);
                    acknowledgment.acknowledge();
                    return;
                }

                String merchantId = json.has("merchantId") ? json.get("merchantId").asText() : null;
                String userId = json.has("userId") ? json.get("userId").asText() : null;
                String sku = json.has("sku") ? json.get("sku").asText() : null;
                Integer quantity = json.has("quantity") ? json.get("quantity").asInt() : 0;
                BigDecimal unitPrice = json.has("unitPrice")
                        ? new BigDecimal(json.get("unitPrice").asText())
                        : BigDecimal.ZERO;
                BigDecimal totalAmount = json.has("totalAmount")
                        ? new BigDecimal(json.get("totalAmount").asText())
                        : BigDecimal.ZERO;
                Instant occurredAt = json.has("occurredAt")
                        ? Instant.parse(json.get("occurredAt").asText())
                        : Instant.now();

                OrderReadModel orderReadModel = new OrderReadModel(
                        orderId, merchantId, userId, sku, quantity,
                        unitPrice, totalAmount, occurredAt
                );
                orderReadModelRepository.save(orderReadModel);
                logger.info("Saved OrderReadModel for order {}", orderId);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Failed to process order event: {}", message, e);
            // Do not acknowledge - will be retried
        }
    }

    @KafkaListener(topics = "merchant.events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleMerchantEvent(String message, Acknowledgment acknowledgment) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String eventType = json.get("eventType").asText();

            if ("MerchantCredited".equals(eventType)) {
                String eventId = json.has("eventId") ? json.get("eventId").asText() : null;
                if (eventId == null) {
                    eventId = json.get("aggregateId").asText() + "-" + System.currentTimeMillis();
                }

                // Idempotency check
                if (merchantCreditReadModelRepository.existsByEventId(eventId)) {
                    logger.info("Merchant credit event {} already processed, skipping", eventId);
                    acknowledgment.acknowledge();
                    return;
                }

                String orderId = json.has("orderId") ? json.get("orderId").asText() : null;
                String merchantId = json.has("merchantId") ? json.get("merchantId").asText() : null;
                BigDecimal amount = json.has("amount")
                        ? new BigDecimal(json.get("amount").asText())
                        : BigDecimal.ZERO;
                Instant occurredAt = json.has("occurredAt")
                        ? Instant.parse(json.get("occurredAt").asText())
                        : Instant.now();

                MerchantCreditReadModel creditReadModel = new MerchantCreditReadModel(
                        eventId, orderId, merchantId, amount, occurredAt
                );
                merchantCreditReadModelRepository.save(creditReadModel);
                logger.info("Saved MerchantCreditReadModel for event {}", eventId);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Failed to process merchant event: {}", message, e);
            // Do not acknowledge - will be retried
        }
    }
}
