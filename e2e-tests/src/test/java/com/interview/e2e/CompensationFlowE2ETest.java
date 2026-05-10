package com.interview.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.saga.SagaCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompensationFlowE2ETest extends KafkaSagaFlowTestSupport {

    @Test
    void shouldPublishFailureEventAndCompensationCommandsOnMerchantCreditFailure() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String userCommandsTopic = randomTopic("user.commands");
        String merchantCommandsTopic = randomTopic("merchant.commands");
        String userEventsTopic = randomTopic("user.events");
        String merchantEventsTopic = randomTopic("merchant.events");

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

        UnifiedSagaFixtures.PaymentDeducted paymentDeducted = new UnifiedSagaFixtures.PaymentDeducted(
                "usr-001", orderId, new BigDecimal("199.98")
        );
        paymentDeducted.correlate(sagaId, deduct.commandId(), orderId);

        UnifiedSagaFixtures.InventoryReserved inventoryReserved = new UnifiedSagaFixtures.InventoryReserved(
                orderId, "SKU-001", 2
        );
        inventoryReserved.correlate(sagaId, reserve.commandId(), orderId);

        UnifiedSagaFixtures.MerchantCreditFailed merchantCreditFailed = new UnifiedSagaFixtures.MerchantCreditFailed(
                orderId, "mrc-001", new BigDecimal("199.98")
        );
        merchantCreditFailed.correlate(sagaId, credit.commandId(), orderId)
                .fail("MERCHANT_CREDIT_FAILED", "credit failed");

        publishMessages(userCommandsTopic, sagaId, List.of(deduct, refund));
        publishMessages(merchantCommandsTopic, sagaId, List.of(reserve, credit, release));
        publishMessage(userEventsTopic, sagaId, paymentDeducted);
        publishMessages(merchantEventsTopic, sagaId, List.of(inventoryReserved, merchantCreditFailed));

        List<JsonNode> userCommands = consumeMessages(userCommandsTopic, 2);
        List<JsonNode> merchantCommands = consumeMessages(merchantCommandsTopic, 3);
        List<JsonNode> userEvents = consumeMessages(userEventsTopic, 1);
        List<JsonNode> merchantEvents = consumeMessages(merchantEventsTopic, 2);

        assertThat(userCommands)
                .extracting(node -> node.get("commandType").asText())
                .containsExactly("DeductPaymentCommand", "RefundPaymentCommand");
        assertThat(userCommands.get(0).get("compensation").asBoolean()).isFalse();
        assertThat(userCommands.get(1).get("compensation").asBoolean()).isTrue();
        assertThat(userCommands.get(1).get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(userCommands.get(1).path("payload").path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));

        assertThat(merchantCommands)
                .extracting(node -> node.get("commandType").asText())
                .containsExactly("ReserveInventoryCommand", "CreditMerchantCommand", "ReleaseInventoryCommand");
        assertThat(merchantCommands.get(0).get("compensation").asBoolean()).isFalse();
        assertThat(merchantCommands.get(1).get("compensation").asBoolean()).isFalse();
        assertThat(merchantCommands.get(2).get("compensation").asBoolean()).isTrue();
        assertThat(merchantCommands.get(2).path("payload").path("sku").asText()).isEqualTo("SKU-001");

        JsonNode paymentEventJson = userEvents.get(0);
        assertThat(paymentEventJson.get("eventType").asText()).isEqualTo("PaymentDeducted");
        assertThat(paymentEventJson.get("commandId").asText()).isEqualTo(deduct.commandId());
        assertThat(paymentEventJson.get("sagaId").asText()).isEqualTo(sagaId);

        assertThat(merchantEvents)
                .extracting(node -> node.get("eventType").asText())
                .containsExactly("InventoryReserved", "MerchantCreditFailed");
        assertThat(merchantEvents.get(0).get("commandId").asText()).isEqualTo(reserve.commandId());
        assertThat(merchantEvents.get(1).get("commandId").asText()).isEqualTo(credit.commandId());
        assertThat(merchantEvents.get(1).get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(merchantEvents.get(1).get("failureCode").asText()).isEqualTo("MERCHANT_CREDIT_FAILED");
        assertThat(merchantEvents.get(1).get("failureReason").asText()).isEqualTo("credit failed");
        assertThat(merchantEvents.get(1).get("merchantId").asText()).isEqualTo("mrc-001");
    }

    @Test
    void shouldSendRefundPaymentCompensationOnInventoryReserveFailure() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String userCommandsTopic = randomTopic("user.commands");
        String merchantCommandsTopic = randomTopic("merchant.commands");
        String userEventsTopic = randomTopic("user.events");
        String merchantEventsTopic = randomTopic("merchant.events");

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> deduct = SagaCommand.of(
                sagaId, orderId, "DeductPaymentCommand", "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("99.50")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.ReserveInventoryPayload> reserve = SagaCommand.of(
                sagaId, orderId, "ReserveInventoryCommand", "merchant-service",
                new UnifiedSagaFixtures.ReserveInventoryPayload("SKU-001", orderId, 5),
                false
        );
        SagaCommand<UnifiedSagaFixtures.RefundPaymentPayload> refund = SagaCommand.of(
                sagaId, orderId, "RefundPaymentCommand", "user-service",
                new UnifiedSagaFixtures.RefundPaymentPayload("usr-001", orderId, new BigDecimal("99.50")),
                true
        );

        UnifiedSagaFixtures.PaymentDeducted paymentDeducted = new UnifiedSagaFixtures.PaymentDeducted(
                "usr-001", orderId, new BigDecimal("99.50")
        );
        paymentDeducted.correlate(sagaId, deduct.commandId(), orderId);

        UnifiedSagaFixtures.InventoryReserveFailed inventoryFailed = new UnifiedSagaFixtures.InventoryReserveFailed(
                orderId, "SKU-001", 5
        );
        inventoryFailed.correlate(sagaId, reserve.commandId(), orderId)
                .fail("INVENTORY_UNAVAILABLE", "Only 2 in stock, 5 requested");

        publishMessages(userCommandsTopic, sagaId, List.of(deduct, refund));
        publishMessages(merchantCommandsTopic, sagaId, List.of(reserve));
        publishMessage(userEventsTopic, sagaId, paymentDeducted);
        publishMessage(merchantEventsTopic, sagaId, inventoryFailed);

        List<JsonNode> userCommands = consumeMessages(userCommandsTopic, 2);
        List<JsonNode> merchantCommands = consumeMessages(merchantCommandsTopic, 1);
        List<JsonNode> userEvents = consumeMessages(userEventsTopic, 1);
        List<JsonNode> merchantEvents = consumeMessages(merchantEventsTopic, 1);

        assertThat(userCommands)
                .extracting(node -> node.get("commandType").asText())
                .containsExactly("DeductPaymentCommand", "RefundPaymentCommand");
        assertThat(userCommands.get(1).get("compensation").asBoolean()).isTrue();
        assertThat(userCommands.get(1).path("payload").path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99.50"));

        assertThat(merchantCommands.get(0).get("commandType").asText())
                .isEqualTo("ReserveInventoryCommand");

        assertThat(userEvents.get(0).get("eventType").asText()).isEqualTo("PaymentDeducted");

        assertThat(merchantEvents.get(0).get("eventType").asText()).isEqualTo("InventoryReserveFailed");
        assertThat(merchantEvents.get(0).get("failureCode").asText()).isEqualTo("INVENTORY_UNAVAILABLE");
        assertThat(merchantEvents.get(0).get("failureReason").asText())
                .isEqualTo("Only 2 in stock, 5 requested");
    }

    @Test
    void shouldFailFastWithoutCompensationOnPaymentDeductFailure() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String userCommandsTopic = randomTopic("user.commands");
        String userEventsTopic = randomTopic("user.events");

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> deduct = SagaCommand.of(
                sagaId, orderId, "DeductPaymentCommand", "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("200.00")),
                false
        );

        UnifiedSagaFixtures.PaymentDeductFailed paymentFailed = new UnifiedSagaFixtures.PaymentDeductFailed(
                "usr-001", orderId, new BigDecimal("200.00")
        );
        paymentFailed.correlate(sagaId, deduct.commandId(), orderId)
                .fail("INSUFFICIENT_BALANCE", "Balance 50.00 < 200.00");

        publishMessage(userCommandsTopic, sagaId, deduct);
        publishMessage(userEventsTopic, sagaId, paymentFailed);

        List<JsonNode> userCommands = consumeMessages(userCommandsTopic, 1);
        List<JsonNode> userEvents = consumeMessages(userEventsTopic, 1);

        assertThat(userCommands.get(0).get("commandType").asText()).isEqualTo("DeductPaymentCommand");
        assertThat(userCommands.get(0).get("compensation").asBoolean()).isFalse();

        assertThat(userEvents.get(0).get("eventType").asText()).isEqualTo("PaymentDeductFailed");
        assertThat(userEvents.get(0).get("failureCode").asText()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(userEvents.get(0).get("failureReason").asText()).isEqualTo("Balance 50.00 < 200.00");
        assertThat(userEvents.get(0).get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(userEvents.get(0).get("commandId").asText()).isEqualTo(deduct.commandId());
        assertThat(userEvents.get(0).get("orderId").asText()).isEqualTo(orderId);
    }
}
