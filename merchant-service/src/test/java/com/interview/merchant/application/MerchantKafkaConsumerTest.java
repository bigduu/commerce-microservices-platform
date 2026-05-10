package com.interview.merchant.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.interview.common.exception.InsufficientInventoryException;
import com.interview.common.idempotency.ProcessedSagaMessageRepository;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.common.saga.SagaCommand;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantKafkaConsumerTest {

    @Mock
    private ProductService productService;

    @Mock
    private MerchantService merchantService;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ProcessedSagaMessageRepository processedSagaMessageRepository;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private MerchantKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new MerchantKafkaConsumer(
                productService,
                merchantService,
                outboxRepository,
                processedSagaMessageRepository,
                objectMapper
        );
    }

    @Test
    void handleCommand_reserveInventory_success() throws Exception {
        String payload = serializeCommand(
                "cmd-1",
                "saga-1",
                "order-1",
                "ReserveInventoryCommand",
                Map.of("orderId", "order-1", "sku", "SKU-001", "quantity", 5),
                false
        );

        consumer.handleCommand(payload, acknowledgment);

        verify(productService).deductInventory("SKU-001", 5);
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertEquals("merchant.events", saved.getTopic());
        assertTrue(saved.getPayload().contains("InventoryReserved"));
        assertTrue(saved.getPayload().contains("\"sagaId\":\"saga-1\""));
        assertTrue(saved.getPayload().contains("\"commandId\":\"cmd-1\""));
    }

    @Test
    void handleCommand_reserveInventory_insufficientInventory() throws Exception {
        String payload = serializeCommand(
                "cmd-2",
                "saga-2",
                "order-2",
                "ReserveInventoryCommand",
                Map.of("orderId", "order-2", "sku", "SKU-001", "quantity", 10),
                false
        );

        doThrow(new InsufficientInventoryException("Not enough stock"))
                .when(productService).deductInventory("SKU-001", 10);

        consumer.handleCommand(payload, acknowledgment);

        verify(productService).deductInventory("SKU-001", 10);
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertEquals("merchant.events", saved.getTopic());
        assertTrue(saved.getPayload().contains("InventoryReserveFailed"));
        assertTrue(saved.getPayload().contains("\"failureCode\":\"INSUFFICIENT_INVENTORY\""));
    }

    @Test
    void handleCommand_releaseInventory_success() throws Exception {
        String payload = serializeCommand(
                "cmd-3",
                "saga-3",
                "order-3",
                "ReleaseInventoryCommand",
                Map.of("orderId", "order-3", "sku", "SKU-001", "quantity", 3),
                true
        );

        consumer.handleCommand(payload, acknowledgment);

        verify(productService).releaseInventory("SKU-001", 3);
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertEquals("merchant.events", saved.getTopic());
        assertTrue(saved.getPayload().contains("InventoryReleased"));
        assertTrue(saved.getPayload().contains("\"sagaId\":\"saga-3\""));
    }

    @Test
    void handleCommand_creditMerchant_success() throws Exception {
        String payload = serializeCommand(
                "cmd-4",
                "saga-4",
                "order-4",
                "CreditMerchantCommand",
                Map.of("orderId", "order-4", "merchantId", "merchant-1", "amount", new BigDecimal("100.00")),
                false
        );

        consumer.handleCommand(payload, acknowledgment);

        verify(merchantService).creditMerchant(eq("merchant-1"), any(BigDecimal.class));
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertEquals("merchant.events", saved.getTopic());
        assertTrue(saved.getPayload().contains("MerchantCredited"));
        assertTrue(saved.getPayload().contains("\"commandId\":\"cmd-4\""));
    }

    @Test
    void handleCommand_creditMerchant_failure_shouldPublishFailureEventAndNotAck() throws Exception {
        String payload = serializeCommand(
                "cmd-5",
                "saga-5",
                "order-5",
                "CreditMerchantCommand",
                Map.of("orderId", "order-5", "merchantId", "merchant-1", "amount", new BigDecimal("100.00")),
                false
        );

        doThrow(new RuntimeException("credit failed")).when(merchantService).creditMerchant(eq("merchant-1"), any(BigDecimal.class));

        consumer.handleCommand(payload, acknowledgment);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertTrue(saved.getPayload().contains("MerchantCreditFailed"));
        assertTrue(saved.getPayload().contains("\"failureCode\":\"MERCHANT_CREDIT_FAILED\""));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleCommand_debitMerchant_success() throws Exception {
        String payload = serializeCommand(
                "cmd-6",
                "saga-6",
                "order-6",
                "DebitMerchantCommand",
                Map.of("orderId", "order-6", "merchantId", "merchant-1", "amount", new BigDecimal("50.00")),
                true
        );

        consumer.handleCommand(payload, acknowledgment);

        verify(merchantService).debitMerchant(eq("merchant-1"), any(BigDecimal.class));
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertEquals("merchant.events", saved.getTopic());
        assertTrue(saved.getPayload().contains("MerchantDebited"));
    }

    @Test
    void handleCommand_unknownCommand_logsWarning() throws Exception {
        String payload = serializeCommand(
                "cmd-7",
                "saga-7",
                "order-7",
                "UnknownCommand",
                Map.of("orderId", "order-7"),
                false
        );

        consumer.handleCommand(payload, acknowledgment);

        verifyNoInteractions(productService, merchantService, outboxRepository);
    }

    @Test
    void handleCommand_duplicateCommand_shouldSkip() throws Exception {
        String payload = serializeCommand(
                "cmd-dup",
                "saga-dup",
                "order-dup",
                "ReserveInventoryCommand",
                Map.of("orderId", "order-dup", "sku", "SKU-001", "quantity", 1),
                false
        );
        when(processedSagaMessageRepository.existsByCommandId("cmd-dup")).thenReturn(true);

        consumer.handleCommand(payload, acknowledgment);

        verify(processedSagaMessageRepository).existsByCommandId("cmd-dup");
        verifyNoInteractions(productService, merchantService, outboxRepository);
    }

    @Test
    void handleCommand_releaseInventory_failure_shouldPublishFailureEventAndNotAck() throws Exception {
        String payload = serializeCommand(
                "cmd-release-fail",
                "saga-rf",
                "order-rf",
                "ReleaseInventoryCommand",
                Map.of("orderId", "order-rf", "sku", "SKU-001", "quantity", 3),
                true
        );

        doThrow(new RuntimeException("release failed")).when(productService).releaseInventory("SKU-001", 3);

        consumer.handleCommand(payload, acknowledgment);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertTrue(saved.getPayload().contains("InventoryReleaseFailed"));
        assertTrue(saved.getPayload().contains("\"failureCode\":\"INVENTORY_RELEASE_FAILED\""));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleCommand_debitMerchant_failure_shouldPublishFailureEventAndNotAck() throws Exception {
        String payload = serializeCommand(
                "cmd-debit-fail",
                "saga-df",
                "order-df",
                "DebitMerchantCommand",
                Map.of("orderId", "order-df", "merchantId", "merchant-1", "amount", new BigDecimal("50.00")),
                true
        );

        doThrow(new RuntimeException("debit failed")).when(merchantService).debitMerchant(eq("merchant-1"), any(BigDecimal.class));

        consumer.handleCommand(payload, acknowledgment);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());
        OutboxMessage saved = captor.getValue();
        assertTrue(saved.getPayload().contains("MerchantDebitFailed"));
        assertTrue(saved.getPayload().contains("\"failureCode\":\"MERCHANT_DEBIT_FAILED\""));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleCommand_blankCommandType_shouldReturn() throws Exception {
        String payload = serializeCommand(
                "cmd-blank",
                "saga-blank",
                "order-blank",
                "",
                Map.of("orderId", "order-blank"),
                false
        );

        consumer.handleCommand(payload, acknowledgment);

        verifyNoInteractions(productService, merchantService, outboxRepository);
    }

    @Test
    void handleCommand_nullCommandType_shouldReturn() throws Exception {
        String payload = serializeCommand(
                "cmd-null",
                "saga-null",
                "order-null",
                null,
                Map.of("orderId", "order-null"),
                false
        );

        consumer.handleCommand(payload, acknowledgment);

        verifyNoInteractions(productService, merchantService, outboxRepository);
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
                "merchant-service",
                null,
                payload,
                compensation
        );
        return objectMapper.writeValueAsString(command);
    }
}
