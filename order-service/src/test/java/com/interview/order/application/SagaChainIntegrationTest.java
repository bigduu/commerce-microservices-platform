package com.interview.order.application;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full saga chain integration test — simulates complete end-to-end saga flows
 * by invoking SagaOrchestrator handler methods in sequence and verifying
 * intermediate state at every step.
 */
@ExtendWith(MockitoExtension.class)
class SagaChainIntegrationTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private Tracer tracer;

    private ObjectMapper objectMapper;
    private SagaOrchestrator orchestrator;

    private static final String USER_ID = "user-001";
    private static final String MERCHANT_ID = "merchant-001";
    private static final String SKU = "SKU-001";
    private static final int QUANTITY = 3;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("50.00");
    private static final BigDecimal TOTAL_AMOUNT = new BigDecimal("150.00");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        Span mockSpan = mock(Span.class);
        lenient().when(tracer.nextSpan()).thenReturn(mockSpan);
        lenient().when(mockSpan.name(any())).thenReturn(mockSpan);
        lenient().when(mockSpan.start()).thenReturn(mockSpan);
        Tracer.SpanInScope mockScope = mock(Tracer.SpanInScope.class);
        lenient().when(tracer.withSpan(mockSpan)).thenReturn(mockScope);

        orchestrator = new SagaOrchestrator(
                sagaInstanceRepository, orderRepository, outboxRepository, objectMapper, tracer
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Order pendingOrder() {
        Order order = new Order("order-1", USER_ID, MERCHANT_ID, SKU, QUANTITY, UNIT_PRICE);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private SagaInstance runningSaga(SagaStep step, String completedSteps) {
        return new SagaInstance("saga-1", "order-1", step, SagaStatus.RUNNING, completedSteps,
                Instant.now().plusSeconds(60));
    }

    private SagaInstance compensatingSaga(SagaStep step, String completedSteps) {
        return new SagaInstance("saga-1", "order-1", step, SagaStatus.COMPENSATING, completedSteps,
                Instant.now().plusSeconds(60));
    }

    private void stubSave() {
        lenient().when(sagaInstanceRepository.save(any(SagaInstance.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(outboxRepository.save(any(OutboxMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private SagaCommand<Map<String, Object>> parseCommand(OutboxMessage msg) {
        try {
            return objectMapper.readValue(msg.getPayload(),
                    objectMapper.getTypeFactory().constructParametricType(SagaCommand.class, Map.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> parseCompletedSteps(SagaInstance saga) {
        try {
            return objectMapper.readValue(saga.getCompletedSteps(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OutboxMessage captureSingleOutbox() {
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private List<OutboxMessage> captureAllOutbox(int expectedCount) {
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    private SagaInstance captureSaga() {
        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private SagaInstance captureLastSaga() {
        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().getLast();
    }

    private List<SagaInstance> captureNSagas(int n) {
        ArgumentCaptor<SagaInstance> captor = ArgumentCaptor.forClass(SagaInstance.class);
        verify(sagaInstanceRepository, times(n)).save(captor.capture());
        return captor.getAllValues();
    }

    private Order captureOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private Order captureLastOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().getLast();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Happy path: DEDUCT → RESERVE → CREDIT → COMPLETED
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Happy path: full 3-step saga completes successfully")
    class HappyPath {

        @Test
        @DisplayName("Step 1→2→3: startSaga → paymentDeducted → inventoryReserved → merchantCredited")
        void fullHappyPath() {
            ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);

            Order order = pendingOrder();
            stubSave();

            // ── Step 1: startSaga ─────────────────────────────────────────
            orchestrator.startSaga(order);

            // ── Step 2: PaymentDeducted → RESERVE_INVENTORY ──────────────
            ArgumentCaptor<SagaInstance> step1Saga = ArgumentCaptor.forClass(SagaInstance.class);
            verify(sagaInstanceRepository).save(step1Saga.capture());
            String sagaId = step1Saga.getValue().getSagaId();

            SagaInstance saga2 = runningSaga(SagaStep.DEDUCT_PAYMENT, "[]");
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(sagaInstanceRepository.findById(sagaId)).thenReturn(Optional.of(saga2));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handlePaymentDeducted(sagaId, "order-1");

            // ── Step 3: InventoryReserved → CREDIT_MERCHANT ──────────────
            SagaInstance saga3 = runningSaga(SagaStep.RESERVE_INVENTORY, "[\"DEDUCT_PAYMENT\"]");
            order.setStatus(OrderStatus.INVENTORY_PROCESSING);
            when(sagaInstanceRepository.findById(sagaId)).thenReturn(Optional.of(saga3));

            orchestrator.handleInventoryReserved(sagaId, "order-1");

            // ── Step 4: MerchantCredited → COMPLETED ─────────────────────
            SagaInstance saga4 = runningSaga(SagaStep.CREDIT_MERCHANT,
                    "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
            order.setStatus(OrderStatus.MERCHANT_CREDITING);
            when(sagaInstanceRepository.findById(sagaId)).thenReturn(Optional.of(saga4));

            orchestrator.handleMerchantCredited(sagaId, "order-1");

            // ── Verify accumulated state ──────────────────────────────────
            verify(sagaInstanceRepository, times(4)).save(sagaCaptor.capture());
            List<SagaInstance> allSagas = sagaCaptor.getAllValues();

            // Step 1: saga created at DEDUCT_PAYMENT, RUNNING
            assertEquals(SagaStep.DEDUCT_PAYMENT, allSagas.get(0).getCurrentStep());
            assertEquals(SagaStatus.RUNNING, allSagas.get(0).getStatus());
            assertEquals("[]", allSagas.get(0).getCompletedSteps());

            // Step 2: advanced to RESERVE_INVENTORY
            assertEquals(SagaStep.RESERVE_INVENTORY, allSagas.get(1).getCurrentStep());
            assertEquals(SagaStatus.RUNNING, allSagas.get(1).getStatus());
            assertEquals(List.of("DEDUCT_PAYMENT"), parseCompletedSteps(allSagas.get(1)));

            // Step 3: advanced to CREDIT_MERCHANT
            assertEquals(SagaStep.CREDIT_MERCHANT, allSagas.get(2).getCurrentStep());
            assertEquals(SagaStatus.RUNNING, allSagas.get(2).getStatus());
            assertEquals(List.of("DEDUCT_PAYMENT", "RESERVE_INVENTORY"), parseCompletedSteps(allSagas.get(2)));

            // Step 4: COMPLETED
            assertEquals(SagaStatus.COMPLETED, allSagas.get(3).getStatus());
            assertEquals(List.of("DEDUCT_PAYMENT", "RESERVE_INVENTORY", "CREDIT_MERCHANT"),
                    parseCompletedSteps(allSagas.get(3)));

            // Order final state is COMPLETED, and orderRepository.save was called 4 times
            // (once per saga step: PAYMENT_PROCESSING, INVENTORY_PROCESSING, MERCHANT_CREDITING, COMPLETED)
            verify(orderRepository, times(4)).save(orderCaptor.capture());
            // All captured references point to the same mutable order object,
            // so verify the final state and the number of save calls
            assertEquals(OrderStatus.COMPLETED, order.getStatus());

            // Outbox: DeductPayment → ReserveInventory → CreditMerchant (3 commands)
            verify(outboxRepository, times(3)).save(outboxCaptor.capture());
            List<OutboxMessage> allOutbox = outboxCaptor.getAllValues();

            assertEquals("user.commands", allOutbox.get(0).getTopic());
            SagaCommand<Map<String, Object>> cmd1 = parseCommand(allOutbox.get(0));
            assertEquals("DeductPaymentCommand", cmd1.commandType());
            assertEquals("user-service", cmd1.targetService());
            assertEquals(USER_ID, cmd1.payload().get("userId"));
            assertEquals(0, TOTAL_AMOUNT.compareTo(new BigDecimal(cmd1.payload().get("amount").toString())));
            assertTrue(!cmd1.compensation());

            assertEquals("merchant.commands", allOutbox.get(1).getTopic());
            SagaCommand<Map<String, Object>> cmd2 = parseCommand(allOutbox.get(1));
            assertEquals("ReserveInventoryCommand", cmd2.commandType());
            assertEquals("merchant-service", cmd2.targetService());
            assertEquals(SKU, cmd2.payload().get("sku"));
            assertEquals(QUANTITY, Integer.parseInt(cmd2.payload().get("quantity").toString()));
            assertTrue(!cmd2.compensation());

            assertEquals("merchant.commands", allOutbox.get(2).getTopic());
            SagaCommand<Map<String, Object>> cmd3 = parseCommand(allOutbox.get(2));
            assertEquals("CreditMerchantCommand", cmd3.commandType());
            assertEquals("merchant-service", cmd3.targetService());
            assertEquals(MERCHANT_ID, cmd3.payload().get("merchantId"));
            assertEquals(0, TOTAL_AMOUNT.compareTo(new BigDecimal(cmd3.payload().get("amount").toString())));
            assertTrue(!cmd3.compensation());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Failure at Step 1: PaymentDeductFailed → no compensation needed
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure at step 1: payment deduct fails, no compensation")
    class PaymentFailurePath {

        @Test
        @DisplayName("startSaga → PaymentDeductFailed → saga FAILED, no outbox")
        void paymentDeductFailed_marksFailedWithoutCompensation() {
            SagaInstance saga = runningSaga(SagaStep.DEDUCT_PAYMENT, "[]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handlePaymentDeductFailed("saga-1", "order-1");

            assertEquals(SagaStatus.FAILED, captureSaga().getStatus());
            assertEquals(OrderStatus.FAILED, captureOrder().getStatus());

            // No compensation commands sent
            verify(outboxRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Failure at Step 2: InventoryReserveFailed → refund payment
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure at step 2: inventory reserve fails, compensate with refund")
    class InventoryFailurePath {

        @Test
        @DisplayName("PaymentDeducted → InventoryReserveFailed → COMPENSATING + RefundPayment")
        void inventoryReserveFailed_sendsRefundPayment() {
            SagaInstance saga = runningSaga(SagaStep.RESERVE_INVENTORY, "[\"DEDUCT_PAYMENT\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.INVENTORY_PROCESSING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleInventoryReserveFailed("saga-1", "order-1");

            assertEquals(SagaStatus.COMPENSATING, captureSaga().getStatus());
            assertEquals(OrderStatus.FAILED, captureOrder().getStatus());

            OutboxMessage outbox = captureSingleOutbox();
            assertEquals("user.commands", outbox.getTopic());
            SagaCommand<Map<String, Object>> cmd = parseCommand(outbox);
            assertEquals("RefundPaymentCommand", cmd.commandType());
            assertEquals("user-service", cmd.targetService());
            assertTrue(cmd.compensation());
            assertEquals(USER_ID, cmd.payload().get("userId"));
            assertEquals(0, TOTAL_AMOUNT.compareTo(new BigDecimal(cmd.payload().get("amount").toString())));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Failure at Step 3: MerchantCreditFailed → release inventory + refund
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure at step 3: merchant credit fails, full rollback")
    class MerchantCreditFailurePath {

        @Test
        @DisplayName("InventoryReserved → MerchantCreditFailed → ReleaseInventory + RefundPayment")
        void merchantCreditFailed_sendsReleaseAndRefund() {
            SagaInstance saga = runningSaga(SagaStep.CREDIT_MERCHANT,
                    "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.MERCHANT_CREDITING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleMerchantCreditFailed("saga-1", "order-1");

            assertEquals(SagaStatus.COMPENSATING, captureSaga().getStatus());
            assertEquals(OrderStatus.FAILED, captureOrder().getStatus());

            List<OutboxMessage> outboxMsgs = captureAllOutbox(2);

            // 1st: ReleaseInventoryCommand → merchant.commands
            OutboxMessage release = outboxMsgs.get(0);
            assertEquals("merchant.commands", release.getTopic());
            SagaCommand<Map<String, Object>> releaseCmd = parseCommand(release);
            assertEquals("ReleaseInventoryCommand", releaseCmd.commandType());
            assertEquals("merchant-service", releaseCmd.targetService());
            assertTrue(releaseCmd.compensation());
            assertEquals(SKU, releaseCmd.payload().get("sku"));
            assertEquals(QUANTITY, Integer.parseInt(releaseCmd.payload().get("quantity").toString()));

            // 2nd: RefundPaymentCommand → user.commands
            OutboxMessage refund = outboxMsgs.get(1);
            assertEquals("user.commands", refund.getTopic());
            SagaCommand<Map<String, Object>> refundCmd = parseCommand(refund);
            assertEquals("RefundPaymentCommand", refundCmd.commandType());
            assertEquals("user-service", refundCmd.targetService());
            assertTrue(refundCmd.compensation());
            assertEquals(USER_ID, refundCmd.payload().get("userId"));
            assertEquals(0, TOTAL_AMOUNT.compareTo(new BigDecimal(refundCmd.payload().get("amount").toString())));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Timeout scenarios
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Timeout handling at each saga step")
    class TimeoutScenarios {

        @Test
        @DisplayName("Timeout at DEDUCT_PAYMENT → mark FAILED, no compensation")
        void timeoutAtDeductPayment() {
            SagaInstance saga = runningSaga(SagaStep.DEDUCT_PAYMENT, "[]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleTimeout("saga-1");

            assertEquals(SagaStatus.FAILED, captureSaga().getStatus());
            assertEquals(OrderStatus.FAILED, captureOrder().getStatus());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("Timeout at RESERVE_INVENTORY → compensate with RefundPayment")
        void timeoutAtReserveInventory() {
            SagaInstance saga = runningSaga(SagaStep.RESERVE_INVENTORY, "[\"DEDUCT_PAYMENT\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.INVENTORY_PROCESSING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleTimeout("saga-1");

            assertEquals(SagaStatus.COMPENSATING, captureSaga().getStatus());
            assertEquals(OrderStatus.FAILED, captureOrder().getStatus());

            OutboxMessage outbox = captureSingleOutbox();
            assertEquals("user.commands", outbox.getTopic());
            assertEquals("RefundPaymentCommand", parseCommand(outbox).commandType());
            assertTrue(parseCommand(outbox).compensation());
        }

        @Test
        @DisplayName("Timeout at CREDIT_MERCHANT → ReleaseInventory + RefundPayment")
        void timeoutAtCreditMerchant() {
            SagaInstance saga = runningSaga(SagaStep.CREDIT_MERCHANT,
                    "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.MERCHANT_CREDITING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleTimeout("saga-1");

            List<OutboxMessage> msgs = captureAllOutbox(2);
            assertEquals("merchant.commands", msgs.get(0).getTopic());
            assertEquals("ReleaseInventoryCommand", parseCommand(msgs.get(0)).commandType());
            assertEquals("user.commands", msgs.get(1).getTopic());
            assertEquals("RefundPaymentCommand", parseCommand(msgs.get(1)).commandType());
        }

        @Test
        @DisplayName("Timeout while COMPENSATING → mark FAILED")
        void timeoutWhileCompensating() {
            SagaInstance saga = compensatingSaga(SagaStep.RESERVE_INVENTORY, "[\"DEDUCT_PAYMENT\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.FAILED);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleTimeout("saga-1");

            assertEquals(SagaStatus.FAILED, captureSaga().getStatus());
            // order already FAILED — no additional save
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Timeout on COMPLETED saga → ignored")
        void timeoutOnCompletedSaga() {
            SagaInstance saga = new SagaInstance("saga-1", "order-1", SagaStep.CREDIT_MERCHANT,
                    SagaStatus.COMPLETED, "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\",\"CREDIT_MERCHANT\"]",
                    Instant.now().minusSeconds(10));
            Order order = pendingOrder();
            order.setStatus(OrderStatus.COMPLETED);

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleTimeout("saga-1");

            verify(sagaInstanceRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Idempotency / duplicate event handling
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Idempotency: duplicate or late events are ignored")
    class IdempotencyScenarios {

        @Test
        @DisplayName("PaymentDeducted on a COMPENSATING saga → ignored")
        void duplicatePaymentDeductedOnCompensatingSaga() {
            SagaInstance saga = compensatingSaga(SagaStep.RESERVE_INVENTORY, "[\"DEDUCT_PAYMENT\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.FAILED);

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handlePaymentDeducted("saga-1", "order-1");

            verify(sagaInstanceRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("InventoryReserved on a FAILED saga → ignored")
        void lateInventoryReservedOnFailedSaga() {
            SagaInstance saga = new SagaInstance("saga-1", "order-1", SagaStep.RESERVE_INVENTORY,
                    SagaStatus.FAILED, "[\"DEDUCT_PAYMENT\"]", Instant.now().plusSeconds(60));
            Order order = pendingOrder();
            order.setStatus(OrderStatus.FAILED);

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleInventoryReserved("saga-1", "order-1");

            verify(sagaInstanceRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("MerchantCredited on a COMPENSATING saga → ignored")
        void lateMerchantCreditedOnCompensatingSaga() {
            SagaInstance saga = compensatingSaga(SagaStep.CREDIT_MERCHANT,
                    "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.FAILED);

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleMerchantCredited("saga-1", "order-1");

            verify(sagaInstanceRepository, never()).save(any());
            verify(orderRepository, never()).save(any());
            verify(outboxRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. Error / not-found scenarios
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling: missing saga or order")
    class ErrorScenarios {

        @Test
        @DisplayName("Any handler throws when saga not found")
        void throwsWhenSagaNotFound() {
            when(sagaInstanceRepository.findById("missing")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handlePaymentDeducted("missing", "order-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handlePaymentDeductFailed("missing", "order-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handleInventoryReserved("missing", "order-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handleInventoryReserveFailed("missing", "order-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handleMerchantCredited("missing", "order-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handleMerchantCreditFailed("missing", "order-1"));
            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handleTimeout("missing"));
        }

        @Test
        @DisplayName("Any handler throws when order not found")
        void throwsWhenOrderNotFound() {
            SagaInstance saga = runningSaga(SagaStep.DEDUCT_PAYMENT, "[]");

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> orchestrator.handlePaymentDeducted("saga-1", "order-1"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. State machine validation: invalid order transitions
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State machine: invalid order status transitions throw")
    class StateMachineValidation {

        @Test
        @DisplayName("startSaga on COMPLETED order throws IllegalStateException")
        void startSagaOnCompletedOrder() {
            Order order = pendingOrder();
            order.setStatus(OrderStatus.COMPLETED);

            assertThrows(IllegalStateException.class, () -> orchestrator.startSaga(order));
        }

        @Test
        @DisplayName("startSaga on FAILED order throws IllegalStateException")
        void startSagaOnFailedOrder() {
            Order order = pendingOrder();
            order.setStatus(OrderStatus.FAILED);

            assertThrows(IllegalStateException.class, () -> orchestrator.startSaga(order));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. Command payload correctness across all steps
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Command payload: verify every command carries correct sagaId/orderId")
    class CommandPayloadCorrectness {

        @Test
        @DisplayName("All forward commands carry sagaId and orderId")
        void forwardCommandsCarryCorrelation() {
            // startSaga
            Order order = pendingOrder();
            stubSave();
            orchestrator.startSaga(order);

            OutboxMessage msg = captureSingleOutbox();
            SagaCommand<Map<String, Object>> cmd = parseCommand(msg);
            assertNotNull(cmd.sagaId());
            assertEquals("order-1", cmd.orderId());
            assertNotNull(cmd.commandId());
            assertNotNull(cmd.createdAt());
        }

        @Test
        @DisplayName("Compensation commands carry compensation=true and correct correlation")
        void compensationCommandsAreFlagged() {
            SagaInstance saga = runningSaga(SagaStep.CREDIT_MERCHANT,
                    "[\"DEDUCT_PAYMENT\",\"RESERVE_INVENTORY\"]");
            Order order = pendingOrder();
            order.setStatus(OrderStatus.MERCHANT_CREDITING);
            stubSave();

            when(sagaInstanceRepository.findById("saga-1")).thenReturn(Optional.of(saga));
            when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

            orchestrator.handleMerchantCreditFailed("saga-1", "order-1");

            List<OutboxMessage> msgs = captureAllOutbox(2);
            for (OutboxMessage m : msgs) {
                SagaCommand<Map<String, Object>> c = parseCommand(m);
                assertTrue(c.compensation());
                assertEquals("saga-1", c.sagaId());
                assertEquals("order-1", c.orderId());
            }
        }
    }
}
