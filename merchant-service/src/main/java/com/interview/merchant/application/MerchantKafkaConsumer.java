package com.interview.merchant.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.event.DomainEvent;
import com.interview.common.exception.InsufficientInventoryException;
import com.interview.common.idempotency.ProcessedSagaMessage;
import com.interview.common.idempotency.ProcessedSagaMessageRepository;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.common.saga.SagaCommand;
import com.interview.common.saga.SagaFailureCodes;
import com.interview.merchant.domain.events.MerchantCreditFailed;
import com.interview.merchant.domain.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class MerchantKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(MerchantKafkaConsumer.class);

    private final ProductService productService;
    private final MerchantService merchantService;
    private final OutboxRepository outboxRepository;
    private final ProcessedSagaMessageRepository processedSagaMessageRepository;
    private final ObjectMapper objectMapper;

    public MerchantKafkaConsumer(ProductService productService,
                                 MerchantService merchantService,
                                 OutboxRepository outboxRepository,
                                 ProcessedSagaMessageRepository processedSagaMessageRepository,
                                 ObjectMapper objectMapper) {
        this.productService = productService;
        this.merchantService = merchantService;
        this.outboxRepository = outboxRepository;
        this.processedSagaMessageRepository = processedSagaMessageRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "merchant.commands", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleCommand(@Payload String payload, Acknowledgment acknowledgment) {
        try {
            SagaCommand<Map<String, Object>> command = objectMapper.readValue(
                    payload,
                    new TypeReference<SagaCommand<Map<String, Object>>>() {}
            );

            if (command.commandType() == null || command.commandType().isBlank()) {
                logger.warn("Unknown command type: {}", payload);
                acknowledgment.acknowledge();
                return;
            }

            if (processedSagaMessageRepository.existsByCommandId(command.commandId())) {
                logger.info("Command {} already processed by merchant consumer, skipping", command.commandId());
                acknowledgment.acknowledge();
                return;
            }

            switch (command.commandType()) {
                case "ReserveInventoryCommand" -> handleReserveInventory(command);
                case "ReleaseInventoryCommand" -> handleReleaseInventory(command);
                case "CreditMerchantCommand" -> handleCreditMerchant(command);
                case "DebitMerchantCommand" -> handleDebitMerchant(command);
                default -> logger.warn("Unknown command type: {}", command.commandType());
            }

            markProcessed(command, "merchant-consumer");
            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Error processing command: {}", payload, e);
        }
    }

    private void handleReserveInventory(SagaCommand<Map<String, Object>> command) {
        String orderId = stringValue(command.payload(), "orderId");
        String sku = stringValue(command.payload(), "sku");
        int quantity = intValue(command.payload(), "quantity");
        try {
            productService.deductInventory(sku, quantity);
            publishEvent(new InventoryReserved(orderId, sku, quantity).correlate(command.sagaId(), command.commandId(), orderId));
        } catch (InsufficientInventoryException e) {
            publishEvent(
                    new InventoryReserveFailed(orderId, sku, e.getMessage())
                            .correlate(command.sagaId(), command.commandId(), orderId)
                            .fail(SagaFailureCodes.INSUFFICIENT_INVENTORY, e.getMessage())
            );
        }
    }

    private void handleReleaseInventory(SagaCommand<Map<String, Object>> command) {
        String orderId = stringValue(command.payload(), "orderId");
        String sku = stringValue(command.payload(), "sku");
        int quantity = intValue(command.payload(), "quantity");
        try {
            productService.releaseInventory(sku, quantity);
            publishEvent(new InventoryReleased(orderId, sku, quantity).correlate(command.sagaId(), command.commandId(), orderId));
        } catch (Exception e) {
            publishEvent(
                    new InventoryReleaseFailed(orderId, sku, quantity)
                            .correlate(command.sagaId(), command.commandId(), orderId)
                            .fail(SagaFailureCodes.INVENTORY_RELEASE_FAILED, e.getMessage())
            );
            throw e;
        }
    }

    private void handleCreditMerchant(SagaCommand<Map<String, Object>> command) {
        String orderId = stringValue(command.payload(), "orderId");
        String merchantId = stringValue(command.payload(), "merchantId");
        BigDecimal amount = decimalValue(command.payload(), "amount");
        try {
            merchantService.creditMerchant(merchantId, amount);
            publishEvent(new MerchantCredited(orderId, merchantId, amount).correlate(command.sagaId(), command.commandId(), orderId));
        } catch (Exception e) {
            publishEvent(
                    new MerchantCreditFailed(orderId, merchantId, amount)
                            .correlate(command.sagaId(), command.commandId(), orderId)
                            .fail(SagaFailureCodes.MERCHANT_CREDIT_FAILED, e.getMessage())
            );
            throw e;
        }
    }

    private void handleDebitMerchant(SagaCommand<Map<String, Object>> command) {
        String orderId = stringValue(command.payload(), "orderId");
        String merchantId = stringValue(command.payload(), "merchantId");
        BigDecimal amount = decimalValue(command.payload(), "amount");
        try {
            merchantService.debitMerchant(merchantId, amount);
            publishEvent(new MerchantDebited(orderId, merchantId, amount).correlate(command.sagaId(), command.commandId(), orderId));
        } catch (Exception e) {
            publishEvent(
                    new MerchantDebitFailed(orderId, merchantId, amount)
                            .correlate(command.sagaId(), command.commandId(), orderId)
                            .fail(SagaFailureCodes.MERCHANT_DEBIT_FAILED, e.getMessage())
            );
            throw e;
        }
    }

    private void markProcessed(SagaCommand<Map<String, Object>> command, String consumerName) {
        processedSagaMessageRepository.save(
                new ProcessedSagaMessage(command.commandId(), command.sagaId(), command.orderId(), consumerName)
        );
    }

    private void publishEvent(DomainEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize merchant domain event", e);
        }
        OutboxMessage message = new OutboxMessage(
                event.getEventId(),
                event.getAggregateType(),
                event.getEventType(),
                json,
                "merchant.events"
        );
        outboxRepository.save(message);
    }

    private String stringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private int intValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private BigDecimal decimalValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : new BigDecimal(value.toString());
    }
}
