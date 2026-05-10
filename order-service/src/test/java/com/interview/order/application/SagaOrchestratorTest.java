package com.interview.order.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private Tracer tracer;

    private ObjectMapper objectMapper;
    private SagaOrchestrator sagaOrchestrator;

    private static final String SAGA_ID = "saga-1";
    private static final String ORDER_ID = "order-1";
    private static final String USER_ID = "user-1";
    private static final String MERCHANT_ID = "merchant-1";
    private static final String SKU = "SKU-001";
    private static final int QUANTITY = 2;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("10.00");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        Span mockSpan = mock(Span.class);
        lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
        lenient().when(mockSpan.name(any())).thenReturn(mockSpan);
        lenient().when(mockSpan.start()).thenReturn(mockSpan);
        Tracer.SpanInScope mockScope = mock(Tracer.SpanInScope.class);
        lenient().when(tracer.withSpan(mockSpan)).thenReturn(mockScope);

        sagaOrchestrator = new SagaOrchestrator(
                sagaInstanceRepository,
                orderRepository,
                outboxRepository,
                objectMapper,
                tracer
        );
    }

    private Order createOrder() {
        Order order = new Order(ORDER_ID, USER_ID, MERCHANT_ID, SKU, QUANTITY, UNIT_PRICE);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private SagaInstance createSaga(SagaStep currentStep, SagaStatus status, String completedSteps) {
        return new SagaInstance(SAGA_ID, ORDER_ID, currentStep, status, completedSteps, Instant.now().plusSeconds(300));
    }

    @Test
    void startSaga_shouldCreateSagaInstance_transitionOrderAndSendDeductPaymentCommand() {
        Order order = createOrder();
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.startSaga(order);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        SagaInstance savedSaga = sagaCaptor.getValue();
        assertNotNull(savedSaga.getSagaId());
        assertEquals(ORDER_ID, savedSaga.getOrderId());
        assertEquals(SagaStep.DEDUCT_PAYMENT, savedSaga.getCurrentStep());
        assertEquals(SagaStatus.RUNNING, savedSaga.getStatus());
        assertEquals("[]", savedSaga.getCompletedSteps());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.PAYMENT_PROCESSING, orderCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxMessage outboxMsg = outboxCaptor.getValue();

        assertEquals("user.commands", outboxMsg.getTopic());

        SagaCommand<Map<String, Object>> command = readCommand(outboxMsg.getPayload());
        assertEquals("DeductPaymentCommand", command.commandType());
        assertEquals(savedSaga.getSagaId(), command.sagaId());
        assertEquals(ORDER_ID, command.orderId());
        assertEquals("user-service", command.targetService());
        assertEquals(USER_ID, command.payload().get("userId"));
        assertEquals(ORDER_ID, command.payload().get("orderId"));
        assertTrue(new BigDecimal(command.payload().get("amount").toString()).compareTo(new BigDecimal("20.00")) == 0);
    }

    @Test
    void handlePaymentDeducted_shouldTransitionToInventoryProcessing_andSendReserveInventoryCommand() throws JsonProcessingException {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        SagaInstance savedSaga = sagaCaptor.getValue();
        assertEquals(SagaStep.RESERVE_INVENTORY, savedSaga.getCurrentStep());
        assertEquals(SagaStatus.RUNNING, savedSaga.getStatus());

        java.util.List<String> completed = objectMapper.readValue(savedSaga.getCompletedSteps(), java.util.List.class);
        assertEquals(1, completed.size());
        assertEquals(SagaStep.DEDUCT_PAYMENT.name(), completed.get(0));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.INVENTORY_PROCESSING, orderCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxMessage> msgCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(msgCaptor.capture());
        SagaCommand<Map<String, Object>> command = readCommand(msgCaptor.getValue().getPayload());
        assertEquals("ReserveInventoryCommand", command.commandType());
        assertEquals(SKU, command.payload().get("sku"));
        assertEquals(ORDER_ID, command.payload().get("orderId"));
        assertEquals(QUANTITY, Integer.parseInt(command.payload().get("quantity").toString()));
    }

    @Test
    void handlePaymentDeducted_shouldIgnore_whenSagaNotRunning() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.FAILED, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.FAILED);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID);

        verify(sagaInstanceRepository, never()).save(any(SagaInstance.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handlePaymentDeductFailed_shouldMarkSagaAndOrderAsFailed() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handlePaymentDeductFailed(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.FAILED, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.FAILED, orderCaptor.getValue().getStatus());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleInventoryReserved_shouldTransitionToMerchantCrediting_andSendCreditMerchantCommand() throws JsonProcessingException {
        SagaInstance saga = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.INVENTORY_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleInventoryReserved(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        SagaInstance savedSaga = sagaCaptor.getValue();
        assertEquals(SagaStep.CREDIT_MERCHANT, savedSaga.getCurrentStep());
        assertEquals(SagaStatus.RUNNING, savedSaga.getStatus());

        java.util.List<String> completed = objectMapper.readValue(savedSaga.getCompletedSteps(), java.util.List.class);
        assertEquals(2, completed.size());
        assertEquals(SagaStep.DEDUCT_PAYMENT.name(), completed.get(0));
        assertEquals(SagaStep.RESERVE_INVENTORY.name(), completed.get(1));

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.MERCHANT_CREDITING, orderCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxMessage> msgCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(msgCaptor.capture());
        SagaCommand<Map<String, Object>> command = readCommand(msgCaptor.getValue().getPayload());
        assertEquals("CreditMerchantCommand", command.commandType());
        assertEquals(MERCHANT_ID, command.payload().get("merchantId"));
        assertEquals(ORDER_ID, command.payload().get("orderId"));
        assertTrue(new BigDecimal(command.payload().get("amount").toString()).compareTo(new BigDecimal("20.00")) == 0);
    }

    @Test
    void handleInventoryReserved_shouldIgnore_whenSagaNotRunning() {
        SagaInstance saga = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.COMPENSATING, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.FAILED);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        sagaOrchestrator.handleInventoryReserved(SAGA_ID, ORDER_ID);

        verify(sagaInstanceRepository, never()).save(any(SagaInstance.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleInventoryReserveFailed_shouldStartCompensation_andSendRefundPaymentCommand() {
        SagaInstance saga = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.INVENTORY_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleInventoryReserveFailed(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.COMPENSATING, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.FAILED, orderCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxMessage> msgCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(msgCaptor.capture());
        OutboxMessage msg = msgCaptor.getValue();
        assertEquals("user.commands", msg.getTopic());
        SagaCommand<Map<String, Object>> command = readCommand(msg.getPayload());
        assertEquals("RefundPaymentCommand", command.commandType());
        assertEquals(USER_ID, command.payload().get("userId"));
        assertEquals(ORDER_ID, command.payload().get("orderId"));
        assertTrue(new BigDecimal(command.payload().get("amount").toString()).compareTo(new BigDecimal("20.00")) == 0);
        assertTrue(command.compensation());
    }

    @Test
    void handleMerchantCredited_shouldMarkSagaCompleted_andOrderCompleted() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.MERCHANT_CREDITING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleMerchantCredited(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.COMPLETED, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.COMPLETED, orderCaptor.getValue().getStatus());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleMerchantCredited_shouldIgnore_whenSagaNotRunning() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.FAILED, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.FAILED);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        sagaOrchestrator.handleMerchantCredited(SAGA_ID, ORDER_ID);

        verify(sagaInstanceRepository, never()).save(any(SagaInstance.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleMerchantCreditFailed_shouldSendReleaseInventoryAndRefundPayment_andMarkFailed() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.MERCHANT_CREDITING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleMerchantCreditFailed(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.COMPENSATING, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.FAILED, orderCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxMessage> msgCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository, times(2)).save(msgCaptor.capture());

        java.util.List<OutboxMessage> msgs = msgCaptor.getAllValues();
        java.util.List<String> topics = msgs.stream().map(OutboxMessage::getTopic).toList();
        java.util.List<String> payloads = msgs.stream().map(OutboxMessage::getPayload).toList();

        assertEquals("merchant.commands", topics.get(0));
        SagaCommand<Map<String, Object>> release = readCommand(payloads.get(0));
        assertEquals("ReleaseInventoryCommand", release.commandType());
        assertEquals(SKU, release.payload().get("sku"));
        assertEquals(ORDER_ID, release.payload().get("orderId"));
        assertEquals(QUANTITY, Integer.parseInt(release.payload().get("quantity").toString()));
        assertTrue(release.compensation());

        assertEquals("user.commands", topics.get(1));
        SagaCommand<Map<String, Object>> refund = readCommand(payloads.get(1));
        assertEquals("RefundPaymentCommand", refund.commandType());
        assertEquals(USER_ID, refund.payload().get("userId"));
        assertEquals(ORDER_ID, refund.payload().get("orderId"));
        assertTrue(new BigDecimal(refund.payload().get("amount").toString()).compareTo(new BigDecimal("20.00")) == 0);
        assertTrue(refund.compensation());
    }

    @Test
    void handlePaymentDeducted_shouldThrow_whenSagaNotFound() {
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID));
        assertEquals("Saga not found: " + SAGA_ID, exception.getMessage());
    }

    @Test
    void handlePaymentDeducted_shouldThrow_whenOrderNotFound() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID));
        assertEquals("Order not found: " + ORDER_ID, exception.getMessage());
    }

    @Test
    void handlePaymentDeductFailed_shouldThrow_whenSagaNotFound() {
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handlePaymentDeductFailed(SAGA_ID, ORDER_ID));
        assertEquals("Saga not found: " + SAGA_ID, exception.getMessage());
    }

    @Test
    void handleInventoryReserved_shouldThrow_whenOrderNotFound() {
        SagaInstance saga = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.RUNNING, "[]");
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handleInventoryReserved(SAGA_ID, ORDER_ID));
        assertEquals("Order not found: " + ORDER_ID, exception.getMessage());
    }

    @Test
    void handleMerchantCredited_shouldThrow_whenSagaNotFound() {
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handleMerchantCredited(SAGA_ID, ORDER_ID));
        assertEquals("Saga not found: " + SAGA_ID, exception.getMessage());
    }

    @Test
    void handleMerchantCreditFailed_shouldThrow_whenOrderNotFound() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.RUNNING, "[]");
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handleMerchantCreditFailed(SAGA_ID, ORDER_ID));
        assertEquals("Order not found: " + ORDER_ID, exception.getMessage());
    }

    @Test
    void appendCompletedStep_shouldHandleMultipleSteps() throws JsonProcessingException {
        SagaInstance saga1 = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");
        Order order1 = createOrder();
        order1.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga1));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order1));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID);

        SagaInstance saga2 = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\"]");
        Order order2 = createOrder();
        order2.setStatus(OrderStatus.INVENTORY_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga2));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order2));

        sagaOrchestrator.handleInventoryReserved(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository, times(2)).save(sagaCaptor.capture());
        java.util.List<SagaInstance> savedSagas = sagaCaptor.getAllValues();

        java.util.List<String> completedFirst = objectMapper.readValue(savedSagas.get(0).getCompletedSteps(), java.util.List.class);
        assertEquals(1, completedFirst.size());
        assertEquals(SagaStep.DEDUCT_PAYMENT.name(), completedFirst.get(0));

        java.util.List<String> completedSecond = objectMapper.readValue(savedSagas.get(1).getCompletedSteps(), java.util.List.class);
        assertEquals(2, completedSecond.size());
        assertEquals(SagaStep.DEDUCT_PAYMENT.name(), completedSecond.get(0));
        assertEquals(SagaStep.RESERVE_INVENTORY.name(), completedSecond.get(1));
    }

    @Test
    void handlePaymentDeducted_shouldAppendCompletedStep_whenCompletedStepsIsNotEmpty() throws JsonProcessingException {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        java.util.List<String> completed = objectMapper.readValue(sagaCaptor.getValue().getCompletedSteps(), java.util.List.class);
        assertEquals(2, completed.size());
        assertEquals(SagaStep.DEDUCT_PAYMENT.name(), completed.get(0));
        assertEquals(SagaStep.DEDUCT_PAYMENT.name(), completed.get(1));
    }

    @Test
    void handleTimeout_shouldMarkFailed_whenDeductPaymentTimedOut() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.FAILED, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.FAILED, orderCaptor.getValue().getStatus());

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleTimeout_shouldRefund_whenReserveInventoryTimedOut() {
        SagaInstance saga = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.INVENTORY_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.COMPENSATING, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<OutboxMessage> msgCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(msgCaptor.capture());
        OutboxMessage msg = msgCaptor.getValue();
        assertEquals("user.commands", msg.getTopic());
        SagaCommand<Map<String, Object>> refund = readCommand(msg.getPayload());
        assertEquals("RefundPaymentCommand", refund.commandType());
        assertTrue(refund.compensation());
    }

    @Test
    void handleTimeout_shouldReleaseInventoryAndRefund_whenCreditMerchantTimedOut() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.RUNNING, "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.MERCHANT_CREDITING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        ArgumentCaptor<OutboxMessage> msgCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository, times(2)).save(msgCaptor.capture());
        assertEquals("merchant.commands", msgCaptor.getAllValues().get(0).getTopic());
        assertEquals("user.commands", msgCaptor.getAllValues().get(1).getTopic());
    }

    @Test
    void handleTimeout_shouldMarkFailed_whenCompensatingTimedOut() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.COMPENSATING, "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.FAILED);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.FAILED, sagaCaptor.getValue().getStatus());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void handleTimeout_shouldIgnore_whenSagaCompleted() {
        SagaInstance saga = createSaga(SagaStep.CREDIT_MERCHANT, SagaStatus.COMPLETED, "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.COMPLETED);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        verify(sagaInstanceRepository, never()).save(any(SagaInstance.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleTimeout_shouldIgnore_whenSagaFailed() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.FAILED, "[]");
        Order order = createOrder();
        order.setStatus(OrderStatus.FAILED);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        verify(sagaInstanceRepository, never()).save(any(SagaInstance.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void handleTimeout_compensatingTimedOut_orderNotFailed_shouldTransitionOrderToFailed() {
        SagaInstance saga = createSaga(SagaStep.RESERVE_INVENTORY, SagaStatus.COMPENSATING, "[\"DEDUCT_PAYMENT\"]");
        Order order = createOrder();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        sagaOrchestrator.handleTimeout(SAGA_ID);

        ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository).save(sagaCaptor.capture());
        assertEquals(SagaStatus.FAILED, sagaCaptor.getValue().getStatus());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.FAILED, orderCaptor.getValue().getStatus());
    }

    @Test
    void handleTimeout_shouldThrow_whenSagaNotFound() {
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handleTimeout(SAGA_ID));
        assertEquals("Saga not found: " + SAGA_ID, ex.getMessage());
    }

    @Test
    void handleTimeout_shouldThrow_whenOrderNotFound() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handleTimeout(SAGA_ID));
        assertEquals("Order not found: " + ORDER_ID, ex.getMessage());
    }

    @Test
    void startSaga_shouldThrow_whenOrderTransitionFails() {
        Order order = createOrder();
        order.setStatus(OrderStatus.COMPLETED);

        assertThrows(IllegalStateException.class,
                () -> sagaOrchestrator.startSaga(order));
    }

    @Test
    void handlePaymentDeducted_shouldThrow_whenCompletedStepsIsCorrupted() {
        SagaInstance saga = new SagaInstance(SAGA_ID, ORDER_ID, SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "invalid-json{", Instant.now().plusSeconds(300));
        Order order = createOrder();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);

        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sagaOrchestrator.handlePaymentDeducted(SAGA_ID, ORDER_ID));
        assertTrue(ex.getMessage().contains("Failed to update completed steps"));
    }

    @Test
    void handleInventoryReserveFailed_shouldThrow_whenSagaNotFound() {
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handleInventoryReserveFailed(SAGA_ID, ORDER_ID));
        assertEquals("Saga not found: " + SAGA_ID, ex.getMessage());
    }

    @Test
    void handlePaymentDeductFailed_shouldThrow_whenOrderNotFound() {
        SagaInstance saga = createSaga(SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]");
        when(sagaInstanceRepository.findById(SAGA_ID)).thenReturn(Optional.of(saga));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> sagaOrchestrator.handlePaymentDeductFailed(SAGA_ID, ORDER_ID));
        assertEquals("Order not found: " + ORDER_ID, ex.getMessage());
    }

    private SagaCommand<Map<String, Object>> readCommand(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    objectMapper.getTypeFactory().constructParametricType(SagaCommand.class, Map.class)
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
