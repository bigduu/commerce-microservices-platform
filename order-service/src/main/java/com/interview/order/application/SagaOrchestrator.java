package com.interview.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.common.saga.SagaCommand;
import com.interview.order.application.commands.SagaCommands;
import com.interview.order.domain.Order;
import com.interview.order.domain.OrderRepository;
import com.interview.order.domain.OrderStatus;
import com.interview.order.domain.SagaInstance;
import com.interview.order.domain.SagaInstanceRepository;
import com.interview.order.domain.SagaStatus;
import com.interview.order.domain.SagaStep;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SagaOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final SagaInstanceRepository sagaInstanceRepository;
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public SagaOrchestrator(SagaInstanceRepository sagaInstanceRepository,
                            OrderRepository orderRepository,
                            OutboxRepository outboxRepository,
                            ObjectMapper objectMapper,
                            Tracer tracer) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    public void startSaga(Order order) {
        Span span = tracer.nextSpan().name("saga.start").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            String sagaId = UUID.randomUUID().toString();
            SagaInstance saga = new SagaInstance(
                    sagaId,
                    order.getOrderId(),
                    SagaStep.DEDUCT_PAYMENT,
                    SagaStatus.RUNNING,
                    "[]",
                    Instant.now().plusSeconds(60)
            );
            sagaInstanceRepository.save(saga);

            order.transitionTo(OrderStatus.PAYMENT_PROCESSING);
            orderRepository.save(order);

            SagaCommand<SagaCommands.DeductPaymentPayload> command = SagaCommand.of(
                    sagaId,
                    order.getOrderId(),
                    "DeductPaymentCommand",
                    "user-service",
                    new SagaCommands.DeductPaymentPayload(
                            order.getUserId(),
                            order.getOrderId(),
                            order.getTotalAmount()
                    ),
                    false
            );
            sendCommand("user.commands", sagaId, command);
            logger.info("Saga {} started for order {}, sent DeductPaymentCommand", sagaId, order.getOrderId());
        } finally {
            span.end();
        }
    }

    public void handlePaymentDeducted(String sagaId, String orderId) {
        Span span = tracer.nextSpan().name("saga.payment.deducted").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            SagaInstance saga = getSagaOrThrow(sagaId);
            Order order = getOrderOrThrow(orderId);

            if (saga.getStatus() != SagaStatus.RUNNING) {
                logger.warn("Saga {} not running, ignoring payment deducted", sagaId);
                return;
            }

            appendCompletedStep(saga, SagaStep.DEDUCT_PAYMENT);
            saga.setCurrentStep(SagaStep.RESERVE_INVENTORY);
            sagaInstanceRepository.save(saga);

            order.transitionTo(OrderStatus.INVENTORY_PROCESSING);
            orderRepository.save(order);

            SagaCommand<SagaCommands.ReserveInventoryPayload> command = SagaCommand.of(
                    sagaId,
                    order.getOrderId(),
                    "ReserveInventoryCommand",
                    "merchant-service",
                    new SagaCommands.ReserveInventoryPayload(
                            order.getSku(),
                            order.getOrderId(),
                            order.getQuantity()
                    ),
                    false
            );
            sendCommand("merchant.commands", sagaId, command);
            logger.info("Saga {} payment deducted, sent ReserveInventoryCommand for order {}", sagaId, orderId);
        } finally {
            span.end();
        }
    }

    public void handlePaymentDeductFailed(String sagaId, String orderId) {
        SagaInstance saga = getSagaOrThrow(sagaId);
        Order order = getOrderOrThrow(orderId);

        saga.setStatus(SagaStatus.FAILED);
        sagaInstanceRepository.save(saga);

        order.transitionTo(OrderStatus.FAILED);
        orderRepository.save(order);

        logger.info("Saga {} payment deduct failed, order {} marked FAILED", sagaId, orderId);
    }

    public void handleInventoryReserved(String sagaId, String orderId) {
        Span span = tracer.nextSpan().name("saga.inventory.reserved").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            SagaInstance saga = getSagaOrThrow(sagaId);
            Order order = getOrderOrThrow(orderId);

            if (saga.getStatus() != SagaStatus.RUNNING) {
                logger.warn("Saga {} not running, ignoring inventory reserved", sagaId);
                return;
            }

            appendCompletedStep(saga, SagaStep.RESERVE_INVENTORY);
            saga.setCurrentStep(SagaStep.CREDIT_MERCHANT);
            sagaInstanceRepository.save(saga);

            order.transitionTo(OrderStatus.MERCHANT_CREDITING);
            orderRepository.save(order);

            SagaCommand<SagaCommands.CreditMerchantPayload> command = SagaCommand.of(
                    sagaId,
                    order.getOrderId(),
                    "CreditMerchantCommand",
                    "merchant-service",
                    new SagaCommands.CreditMerchantPayload(
                            order.getMerchantId(),
                            order.getOrderId(),
                            order.getTotalAmount()
                    ),
                    false
            );
            sendCommand("merchant.commands", sagaId, command);
            logger.info("Saga {} inventory reserved, sent CreditMerchantCommand for order {}", sagaId, orderId);
        } finally {
            span.end();
        }
    }

    public void handleInventoryReserveFailed(String sagaId, String orderId) {
        SagaInstance saga = getSagaOrThrow(sagaId);
        Order order = getOrderOrThrow(orderId);

        saga.setStatus(SagaStatus.COMPENSATING);
        sagaInstanceRepository.save(saga);

        order.transitionTo(OrderStatus.FAILED);
        orderRepository.save(order);

        SagaCommand<SagaCommands.RefundPaymentPayload> refundCommand = SagaCommand.of(
                sagaId,
                order.getOrderId(),
                "RefundPaymentCommand",
                "user-service",
                new SagaCommands.RefundPaymentPayload(
                        order.getUserId(),
                        order.getOrderId(),
                        order.getTotalAmount()
                ),
                true
        );
        sendCommand("user.commands", sagaId, refundCommand);
        logger.info("Saga {} inventory reserve failed, sent RefundPaymentCommand for order {}", sagaId, orderId);
    }

    public void handleMerchantCredited(String sagaId, String orderId) {
        Span span = tracer.nextSpan().name("saga.merchant.credited").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            SagaInstance saga = getSagaOrThrow(sagaId);
            Order order = getOrderOrThrow(orderId);

            if (saga.getStatus() != SagaStatus.RUNNING) {
                logger.warn("Saga {} not running, ignoring merchant credited", sagaId);
                return;
            }

            appendCompletedStep(saga, SagaStep.CREDIT_MERCHANT);
            saga.setStatus(SagaStatus.COMPLETED);
            sagaInstanceRepository.save(saga);

            order.transitionTo(OrderStatus.COMPLETED);
            orderRepository.save(order);

            logger.info("Saga {} completed for order {}", sagaId, orderId);
        } finally {
            span.end();
        }
    }

    public void handleMerchantCreditFailed(String sagaId, String orderId) {
        SagaInstance saga = getSagaOrThrow(sagaId);
        Order order = getOrderOrThrow(orderId);

        saga.setStatus(SagaStatus.COMPENSATING);
        sagaInstanceRepository.save(saga);

        order.transitionTo(OrderStatus.FAILED);
        orderRepository.save(order);

        SagaCommand<SagaCommands.ReleaseInventoryPayload> releaseCommand = SagaCommand.of(
                sagaId,
                order.getOrderId(),
                "ReleaseInventoryCommand",
                "merchant-service",
                new SagaCommands.ReleaseInventoryPayload(
                        order.getSku(),
                        order.getOrderId(),
                        order.getQuantity()
                ),
                true
        );
        sendCommand("merchant.commands", sagaId, releaseCommand);

        SagaCommand<SagaCommands.RefundPaymentPayload> refundCommand = SagaCommand.of(
                sagaId,
                order.getOrderId(),
                "RefundPaymentCommand",
                "user-service",
                new SagaCommands.RefundPaymentPayload(
                        order.getUserId(),
                        order.getOrderId(),
                        order.getTotalAmount()
                ),
                true
        );
        sendCommand("user.commands", sagaId, refundCommand);

        logger.info("Saga {} merchant credit failed, sent ReleaseInventory + RefundPayment for order {}", sagaId, orderId);
    }

    public void handleTimeout(String sagaId) {
        SagaInstance saga = getSagaOrThrow(sagaId);
        Order order = getOrderOrThrow(saga.getOrderId());

        if (saga.getStatus() == SagaStatus.COMPLETED || saga.getStatus() == SagaStatus.FAILED) {
            logger.info("Saga {} already terminal, ignoring timeout", sagaId);
            return;
        }

        if (saga.getStatus() == SagaStatus.COMPENSATING) {
            saga.setStatus(SagaStatus.FAILED);
            sagaInstanceRepository.save(saga);
            if (order.getStatus() != OrderStatus.FAILED) {
                order.transitionTo(OrderStatus.FAILED);
                orderRepository.save(order);
            }
            logger.warn("Saga {} compensation timed out, marked FAILED", sagaId);
            return;
        }

        switch (saga.getCurrentStep()) {
            case DEDUCT_PAYMENT -> {
                saga.setStatus(SagaStatus.FAILED);
                sagaInstanceRepository.save(saga);
                order.transitionTo(OrderStatus.FAILED);
                orderRepository.save(order);
                logger.warn("Saga {} timed out before payment deduction completed, marked FAILED", sagaId);
            }
            case RESERVE_INVENTORY -> handleInventoryReserveFailed(sagaId, order.getOrderId());
            case CREDIT_MERCHANT -> handleMerchantCreditFailed(sagaId, order.getOrderId());
        }
    }

    private SagaInstance getSagaOrThrow(String sagaId) {
        return sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
    }

    private Order getOrderOrThrow(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }

    private void appendCompletedStep(SagaInstance saga, SagaStep step) {
        try {
            List<String> steps = new ArrayList<>(
                    List.of(objectMapper.readValue(saga.getCompletedSteps(), String[].class))
            );
            steps.add(step.name());
            saga.setCompletedSteps(objectMapper.writeValueAsString(steps));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to update completed steps", e);
        }
    }

    private void sendCommand(String topic, String sagaId, SagaCommand<?> command) {
        try {
            String payload = objectMapper.writeValueAsString(command);
            OutboxMessage message = new OutboxMessage(
                    command.commandId(),
                    "SagaCommand",
                    command.commandType(),
                    payload,
                    topic
            );
            outboxRepository.save(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize saga command", e);
        }
    }
}
