package com.interview.user.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.domain.Money;
import com.interview.common.event.DomainEvent;
import com.interview.common.exception.InsufficientBalanceException;
import com.interview.common.idempotency.ProcessedSagaMessage;
import com.interview.common.idempotency.ProcessedSagaMessageRepository;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.common.saga.SagaCommand;
import com.interview.common.saga.SagaFailureCodes;
import com.interview.user.domain.UserAccount;
import com.interview.user.domain.UserAccountRepository;
import com.interview.user.domain.events.UserAccountEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class UserKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(UserKafkaConsumer.class);

    private final UserAccountService userAccountService;
    private final UserAccountRepository userAccountRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedSagaMessageRepository processedSagaMessageRepository;
    private final ObjectMapper objectMapper;

    public UserKafkaConsumer(UserAccountService userAccountService,
                             UserAccountRepository userAccountRepository,
                             OutboxRepository outboxRepository,
                             ProcessedSagaMessageRepository processedSagaMessageRepository,
                             ObjectMapper objectMapper) {
        this.userAccountService = userAccountService;
        this.userAccountRepository = userAccountRepository;
        this.outboxRepository = outboxRepository;
        this.processedSagaMessageRepository = processedSagaMessageRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "user.commands", groupId = "${spring.kafka.consumer.group-id}")
    public void handleCommand(String message, Acknowledgment acknowledgment) {
        SagaCommand<Map<String, Object>> command = null;
        try {
            command = objectMapper.readValue(
                    message,
                    new TypeReference<SagaCommand<Map<String, Object>>>() {}
            );

            if (command.commandType() == null || command.commandType().isBlank()) {
                logger.warn("Unknown command type: {}", message);
                acknowledgment.acknowledge();
                return;
            }

            if (processedSagaMessageRepository.existsByCommandId(command.commandId())) {
                logger.info("Command {} already processed by user consumer, skipping", command.commandId());
                acknowledgment.acknowledge();
                return;
            }

            switch (command.commandType()) {
                case "DeductPaymentCommand" -> handleDeductPayment(command);
                case "RefundPaymentCommand" -> handleRefundPayment(command);
                default -> logger.warn("Unknown command type: {}", command.commandType());
            }

            markProcessed(command, "user-consumer");
            acknowledgment.acknowledge();
        } catch (InsufficientBalanceException e) {
            logger.warn("Business failure while processing user command: {}", e.getMessage());
            if (command != null) {
                markProcessed(command, "user-consumer");
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Failed to process command: {}", message, e);
            // Do not acknowledge - will be retried
        }
    }

    private void handleDeductPayment(SagaCommand<Map<String, Object>> command) {
        String userId = stringValue(command.payload(), "userId");
        String orderId = stringValue(command.payload(), "orderId");
        BigDecimal amount = decimalValue(command.payload(), "amount");

        try {
            UserAccount account = userAccountService.getUser(userId);
            account.deductPayment(orderId, Money.of(amount));
            account.getPendingEvents().forEach(event -> event.correlate(command.sagaId(), command.commandId(), orderId));
            userAccountRepository.save(account);
            logger.info("Payment deducted for user {}, order {}, amount {}", userId, orderId, amount);
        } catch (InsufficientBalanceException e) {
            publishEvent(
                    new UserAccountEvents.PaymentDeductFailed(userId, orderId, amount)
                            .correlate(command.sagaId(), command.commandId(), orderId)
                            .fail(SagaFailureCodes.INSUFFICIENT_BALANCE, e.getMessage())
            );
            throw e;
        }
    }

    private void handleRefundPayment(SagaCommand<Map<String, Object>> command) {
        String userId = stringValue(command.payload(), "userId");
        String orderId = stringValue(command.payload(), "orderId");
        BigDecimal amount = decimalValue(command.payload(), "amount");

        UserAccount account = userAccountService.getUser(userId);
        account.refundPayment(orderId, Money.of(amount));
        account.getPendingEvents().forEach(event -> event.correlate(command.sagaId(), command.commandId(), orderId));
        userAccountRepository.save(account);
        logger.info("Payment refunded for user {}, order {}, amount {}", userId, orderId, amount);
    }

    private void markProcessed(SagaCommand<Map<String, Object>> command, String consumerName) {
        processedSagaMessageRepository.save(
                new ProcessedSagaMessage(command.commandId(), command.sagaId(), command.orderId(), consumerName)
        );
    }

    private void publishEvent(DomainEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize user domain event", e);
        }

        OutboxMessage message = new OutboxMessage(
                event.getEventId(),
                event.getAggregateType(),
                event.getEventType(),
                payload,
                "user-account.events"
        );
        outboxRepository.save(message);
    }

    private String stringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private BigDecimal decimalValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        return new BigDecimal(value.toString());
    }
}
