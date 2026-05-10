package com.interview.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.interview.common.domain.Money;
import com.interview.common.idempotency.ProcessedSagaMessageRepository;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.common.saga.SagaCommand;
import com.interview.user.domain.UserAccount;
import com.interview.user.domain.UserAccountRepository;
import com.interview.user.domain.events.UserAccountEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserKafkaConsumerTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ProcessedSagaMessageRepository processedSagaMessageRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private UserKafkaConsumer userKafkaConsumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        userKafkaConsumer = new UserKafkaConsumer(
                userAccountService,
                userAccountRepository,
                outboxRepository,
                processedSagaMessageRepository,
                objectMapper
        );
    }

    @Test
    void handleCommand_deductPaymentCommand_shouldDeductPaymentCorrelateEventAndAcknowledge() throws Exception {
        String userId = "user-1";
        String orderId = "order-1";
        BigDecimal amount = new BigDecimal("50.00");
        UserAccount account = UserAccount.create(userId, "alice");
        account.topUp(Money.of(100.00));
        account.clearPendingEvents();

        when(userAccountService.getUser(userId)).thenReturn(account);

        String message = serializeCommand(
                "cmd-1",
                "saga-1",
                orderId,
                "DeductPaymentCommand",
                Map.of("userId", userId, "orderId", orderId, "amount", amount),
                false
        );

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService).getUser(userId);
        verify(userAccountRepository).save(account);
        verify(acknowledgment).acknowledge();
        verifyNoInteractions(outboxRepository);

        assertEquals(Money.of(50.00), account.getBalance());
        assertEquals(1, account.getPendingEvents().size());
        UserAccountEvents.PaymentDeducted event = (UserAccountEvents.PaymentDeducted) account.getPendingEvents().get(0);
        assertEquals("saga-1", event.getSagaId());
        assertEquals("cmd-1", event.getCommandId());
        assertEquals(orderId, event.getOrderId());
    }

    @Test
    void handleCommand_refundPaymentCommand_shouldRefundCorrelateEventAndAcknowledge() throws Exception {
        String userId = "user-1";
        String orderId = "order-1";
        BigDecimal amount = new BigDecimal("25.00");
        UserAccount account = UserAccount.create(userId, "alice");
        account.topUp(Money.of(100.00));
        account.clearPendingEvents();

        when(userAccountService.getUser(userId)).thenReturn(account);

        String message = serializeCommand(
                "cmd-2",
                "saga-2",
                orderId,
                "RefundPaymentCommand",
                Map.of("userId", userId, "orderId", orderId, "amount", amount),
                true
        );

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService).getUser(userId);
        verify(userAccountRepository).save(account);
        verify(acknowledgment).acknowledge();
        verifyNoInteractions(outboxRepository);

        assertEquals(Money.of(125.00), account.getBalance());
        assertEquals(1, account.getPendingEvents().size());
        UserAccountEvents.PaymentRefunded event = (UserAccountEvents.PaymentRefunded) account.getPendingEvents().get(0);
        assertEquals("saga-2", event.getSagaId());
        assertEquals("cmd-2", event.getCommandId());
        assertEquals(orderId, event.getOrderId());
    }

    @Test
    void handleCommand_insufficientBalance_shouldPublishFailureEventAndAcknowledge() throws Exception {
        String userId = "user-1";
        String orderId = "order-1";
        BigDecimal amount = new BigDecimal("50.00");
        UserAccount account = UserAccount.create(userId, "alice");
        account.topUp(Money.of(10.00));
        account.clearPendingEvents();

        when(userAccountService.getUser(userId)).thenReturn(account);

        String message = serializeCommand(
                "cmd-3",
                "saga-3",
                orderId,
                "DeductPaymentCommand",
                Map.of("userId", userId, "orderId", orderId, "amount", amount),
                false
        );

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService).getUser(userId);
        verify(userAccountRepository, never()).save(account);
        verify(acknowledgment).acknowledge();

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertEquals("user-account.events", saved.getTopic());
        assertTrue(saved.getPayload().contains("PaymentDeductFailed"));
        assertTrue(saved.getPayload().contains("\"sagaId\":\"saga-3\""));
        assertTrue(saved.getPayload().contains("\"commandId\":\"cmd-3\""));
        assertTrue(saved.getPayload().contains("\"failureCode\":\"INSUFFICIENT_BALANCE\""));
    }

    @Test
    void handleCommand_unknownCommandType_shouldWarnAndAcknowledge() throws Exception {
        String message = serializeCommand(
                "cmd-4",
                "saga-4",
                "order-4",
                "UnknownCommand",
                Map.of("userId", "user-1"),
                false
        );

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService, never()).getUser(any());
        verify(userAccountRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCommand_invalidJson_shouldNotAcknowledge() {
        String message = "not-valid-json";

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService, never()).getUser(any());
        verify(userAccountRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleCommand_duplicateCommand_shouldSkipAndAcknowledge() throws Exception {
        String message = serializeCommand(
                "cmd-dup",
                "saga-dup",
                "order-dup",
                "DeductPaymentCommand",
                Map.of("userId", "user-1", "orderId", "order-dup", "amount", new BigDecimal("10.00")),
                false
        );
        when(processedSagaMessageRepository.existsByCommandId("cmd-dup")).thenReturn(true);

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(processedSagaMessageRepository).existsByCommandId("cmd-dup");
        verify(userAccountService, never()).getUser(any());
        verify(userAccountRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCommand_serviceException_shouldNotAcknowledge() throws Exception {
        String userId = "user-1";
        String orderId = "order-1";
        BigDecimal amount = new BigDecimal("50.00");
        String message = serializeCommand(
                "cmd-5",
                "saga-5",
                orderId,
                "DeductPaymentCommand",
                Map.of("userId", userId, "orderId", orderId, "amount", amount),
                false
        );

        when(userAccountService.getUser(userId)).thenThrow(new RuntimeException("Service error"));

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService).getUser(userId);
        verify(userAccountRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleCommand_blankCommandType_shouldAcknowledge() throws Exception {
        String message = serializeCommand(
                "cmd-blank",
                "saga-blank",
                "order-blank",
                "",
                Map.of("userId", "user-1"),
                false
        );

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService, never()).getUser(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCommand_nullCommandType_shouldAcknowledge() throws Exception {
        String message = serializeCommand(
                "cmd-null",
                "saga-null",
                "order-null",
                null,
                Map.of("userId", "user-1"),
                false
        );

        userKafkaConsumer.handleCommand(message, acknowledgment);

        verify(userAccountService, never()).getUser(any());
        verify(acknowledgment).acknowledge();
    }

    private String serializeCommand(String commandId,
                                    String sagaId,
                                    String orderId,
                                    String commandType,
                                    Map<String, Object> payload,
                                    boolean compensation) throws Exception {
        SagaCommand<Map<String, Object>> command = new SagaCommand<>(
                commandId,
                sagaId,
                orderId,
                commandType,
                "user-service",
                null,
                payload,
                compensation
        );
        return objectMapper.writeValueAsString(command);
    }
}
