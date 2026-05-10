package com.interview.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.saga.SagaCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SagaContractE2ETest {

    @Test
    void sagaCommand_shouldSerializeUnifiedEnvelope() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> command = SagaCommand.of(
                sagaId,
                orderId,
                "DeductPaymentCommand",
                "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("199.98")),
                false
        );

        JsonNode json = UnifiedSagaFixtures.OBJECT_MAPPER.readTree(
                UnifiedSagaFixtures.OBJECT_MAPPER.writeValueAsString(command)
        );

        assertThat(command.commandId()).isNotBlank();
        assertThat(command.createdAt()).isNotNull();
        assertThat(json.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(json.get("orderId").asText()).isEqualTo(orderId);
        assertThat(json.get("commandType").asText()).isEqualTo("DeductPaymentCommand");
        assertThat(json.get("targetService").asText()).isEqualTo("user-service");
        assertThat(json.get("compensation").asBoolean()).isFalse();
        assertThat(json.path("payload").path("userId").asText()).isEqualTo("usr-001");
        assertThat(json.path("payload").path("orderId").asText()).isEqualTo(orderId);
        assertThat(json.path("payload").path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));
    }

    @Test
    void domainEvent_shouldCarryCorrelationAndFailureMetadata() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();

        UnifiedSagaFixtures.MerchantCreditFailed event = new UnifiedSagaFixtures.MerchantCreditFailed(
                orderId,
                "mrc-001",
                new BigDecimal("199.98")
        );
        event.correlate(sagaId, commandId, orderId)
                .fail("MERCHANT_CREDIT_FAILED", "credit balance update failed");

        JsonNode json = UnifiedSagaFixtures.OBJECT_MAPPER.readTree(
                UnifiedSagaFixtures.OBJECT_MAPPER.writeValueAsString(event)
        );

        assertThat(json.get("eventType").asText()).isEqualTo("MerchantCreditFailed");
        assertThat(json.get("aggregateId").asText()).isEqualTo(orderId);
        assertThat(json.get("aggregateType").asText()).isEqualTo("MerchantAccount");
        assertThat(json.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(json.get("commandId").asText()).isEqualTo(commandId);
        assertThat(json.get("orderId").asText()).isEqualTo(orderId);
        assertThat(json.get("failureCode").asText()).isEqualTo("MERCHANT_CREDIT_FAILED");
        assertThat(json.get("failureReason").asText()).isEqualTo("credit balance update failed");
        assertThat(json.get("merchantId").asText()).isEqualTo("mrc-001");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("199.98"));
    }

    @Test
    void successAndCompensationSequences_shouldUseUnifiedEnvelope() {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> deduct = SagaCommand.of(
                sagaId,
                orderId,
                "DeductPaymentCommand",
                "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("199.98")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.ReserveInventoryPayload> reserve = SagaCommand.of(
                sagaId,
                orderId,
                "ReserveInventoryCommand",
                "merchant-service",
                new UnifiedSagaFixtures.ReserveInventoryPayload("SKU-001", orderId, 2),
                false
        );
        SagaCommand<UnifiedSagaFixtures.CreditMerchantPayload> credit = SagaCommand.of(
                sagaId,
                orderId,
                "CreditMerchantCommand",
                "merchant-service",
                new UnifiedSagaFixtures.CreditMerchantPayload("mrc-001", orderId, new BigDecimal("199.98")),
                false
        );

        SagaCommand<UnifiedSagaFixtures.ReleaseInventoryPayload> release = SagaCommand.of(
                sagaId,
                orderId,
                "ReleaseInventoryCommand",
                "merchant-service",
                new UnifiedSagaFixtures.ReleaseInventoryPayload("SKU-001", orderId, 2),
                true
        );
        SagaCommand<UnifiedSagaFixtures.RefundPaymentPayload> refund = SagaCommand.of(
                sagaId,
                orderId,
                "RefundPaymentCommand",
                "user-service",
                new UnifiedSagaFixtures.RefundPaymentPayload("usr-001", orderId, new BigDecimal("199.98")),
                true
        );

        List<SagaCommand<?>> forward = List.of(deduct, reserve, credit);
        List<SagaCommand<?>> compensation = List.of(release, refund);

        assertThat(forward)
                .extracting(SagaCommand::commandType)
                .containsExactly("DeductPaymentCommand", "ReserveInventoryCommand", "CreditMerchantCommand");
        assertThat(forward)
                .extracting(SagaCommand::compensation)
                .containsOnly(false);
        assertThat(compensation)
                .extracting(SagaCommand::commandType)
                .containsExactly("ReleaseInventoryCommand", "RefundPaymentCommand");
        assertThat(compensation)
                .extracting(SagaCommand::compensation)
                .containsOnly(true);
        assertThat(compensation)
                .extracting(SagaCommand::targetService)
                .containsExactly("merchant-service", "user-service");
        assertThat(compensation)
                .allSatisfy(command -> {
                    assertThat(command.sagaId()).isEqualTo(sagaId);
                    assertThat(command.orderId()).isEqualTo(orderId);
                });
    }
}
