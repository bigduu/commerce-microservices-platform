package com.interview.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.saga.SagaCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SagaResilienceE2ETest extends KafkaSagaFlowTestSupport {

    @Test
    void shouldProduceIdenticalEnvelopeForSameCommandId() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();

        String userCommandsTopic = randomTopic("user.commands");

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> command1 = new SagaCommand<>(
                commandId, sagaId, orderId, "DeductPaymentCommand", "user-service", null,
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("100.00")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> command2 = new SagaCommand<>(
                commandId, sagaId, orderId, "DeductPaymentCommand", "user-service", null,
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("100.00")),
                false
        );

        publishMessage(userCommandsTopic, sagaId, command1);
        publishMessage(userCommandsTopic, sagaId, command2);

        List<JsonNode> commands = consumeMessages(userCommandsTopic, 2);

        assertThat(commands.get(0).get("commandId").asText()).isEqualTo(commandId);
        assertThat(commands.get(1).get("commandId").asText()).isEqualTo(commandId);
        assertThat(commands.get(0).get("commandType").asText()).isEqualTo("DeductPaymentCommand");
        assertThat(commands.get(1).get("commandType").asText()).isEqualTo("DeductPaymentCommand");
        assertThat(commands.get(0).get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(commands.get(1).get("sagaId").asText()).isEqualTo(sagaId);
    }

    @Test
    void shouldNotInterfereBetweenConcurrentSagas() throws Exception {
        String sagaA = UUID.randomUUID().toString();
        String sagaB = UUID.randomUUID().toString();
        String orderA = UUID.randomUUID().toString();
        String orderB = UUID.randomUUID().toString();

        String userCommandsTopic = randomTopic("user.commands");
        String merchantCommandsTopic = randomTopic("merchant.commands");

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> deductA = SagaCommand.of(
                sagaA, orderA, "DeductPaymentCommand", "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-a", orderA, new BigDecimal("50.00")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.ReserveInventoryPayload> reserveA = SagaCommand.of(
                sagaA, orderA, "ReserveInventoryCommand", "merchant-service",
                new UnifiedSagaFixtures.ReserveInventoryPayload("SKU-A", orderA, 1),
                false
        );
        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> deductB = SagaCommand.of(
                sagaB, orderB, "DeductPaymentCommand", "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-b", orderB, new BigDecimal("75.00")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.ReserveInventoryPayload> reserveB = SagaCommand.of(
                sagaB, orderB, "ReserveInventoryCommand", "merchant-service",
                new UnifiedSagaFixtures.ReserveInventoryPayload("SKU-B", orderB, 3),
                false
        );

        publishMessages(userCommandsTopic, sagaA, List.of(deductA));
        publishMessages(userCommandsTopic, sagaB, List.of(deductB));
        publishMessages(merchantCommandsTopic, sagaA, List.of(reserveA));
        publishMessages(merchantCommandsTopic, sagaB, List.of(reserveB));

        List<JsonNode> userCommands = consumeMessages(userCommandsTopic, 2);
        List<JsonNode> merchantCommands = consumeMessages(merchantCommandsTopic, 2);

        List<String> userSagaIds = userCommands.stream().map(n -> n.get("sagaId").asText()).toList();
        assertThat(userSagaIds).containsExactlyInAnyOrder(sagaA, sagaB);

        List<String> userOrderIds = userCommands.stream().map(n -> n.get("orderId").asText()).toList();
        assertThat(userOrderIds).containsExactlyInAnyOrder(orderA, orderB);

        assertThat(merchantCommands)
                .extracting(n -> n.get("commandType").asText())
                .containsOnly("ReserveInventoryCommand");
        assertThat(merchantCommands)
                .extracting(n -> n.path("payload").path("sku").asText())
                .containsExactlyInAnyOrder("SKU-A", "SKU-B");
    }

    @Test
    void shouldCarryAllRequiredCorrelationFields() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String topic = randomTopic("user.commands");

        SagaCommand<UnifiedSagaFixtures.CreditMerchantPayload> command = SagaCommand.of(
                sagaId, orderId, "CreditMerchantCommand", "merchant-service",
                new UnifiedSagaFixtures.CreditMerchantPayload("mrc-001", orderId, new BigDecimal("150.00")),
                false
        );

        publishMessage(topic, sagaId, command);

        List<JsonNode> commands = consumeMessages(topic, 1);
        JsonNode json = commands.get(0);

        assertThat(json.get("commandId").asText()).isNotEmpty();
        assertThat(json.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(json.get("orderId").asText()).isEqualTo(orderId);
        assertThat(json.get("commandType").asText()).isEqualTo("CreditMerchantCommand");
        assertThat(json.get("targetService").asText()).isEqualTo("merchant-service");
        assertThat(json.get("compensation").asBoolean()).isFalse();
        assertThat(json.has("createdAt")).isTrue();
        assertThat(json.has("payload")).isTrue();
    }

    @Test
    void shouldSetCompensationFlagOnRefundCommands() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String topic = randomTopic("user.commands");

        SagaCommand<UnifiedSagaFixtures.RefundPaymentPayload> refund = SagaCommand.of(
                sagaId, orderId, "RefundPaymentCommand", "user-service",
                new UnifiedSagaFixtures.RefundPaymentPayload("usr-001", orderId, new BigDecimal("99.99")),
                true
        );

        publishMessage(topic, sagaId, refund);

        List<JsonNode> commands = consumeMessages(topic, 1);
        JsonNode json = commands.get(0);

        assertThat(json.get("commandType").asText()).isEqualTo("RefundPaymentCommand");
        assertThat(json.get("compensation").asBoolean()).isTrue();
        assertThat(json.get("targetService").asText()).isEqualTo("user-service");
        assertThat(json.path("payload").path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("99.99"));
    }

    @Test
    void shouldHaveCorrectOutboxMessageStructure() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        String topic = randomTopic("user-account.events");

        UnifiedSagaFixtures.PaymentDeducted event = new UnifiedSagaFixtures.PaymentDeducted(
                "usr-001", orderId, new BigDecimal("50.00")
        );
        event.correlate(sagaId, UUID.randomUUID().toString(), orderId);
        event.setEventId(eventId);

        publishMessage(topic, sagaId, event);

        List<JsonNode> events = consumeMessages(topic, 1);
        JsonNode json = events.get(0);

        assertThat(json.get("eventType").asText()).isEqualTo("PaymentDeducted");
        assertThat(json.get("eventId").asText()).isEqualTo(eventId);
        assertThat(json.get("aggregateType").asText()).isEqualTo("UserAccount");
        assertThat(json.get("orderId").asText()).isEqualTo(orderId);
        assertThat(json.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(json.get("commandId").asText()).isNotEmpty();
        assertThat(json.get("userId").asText()).isEqualTo("usr-001");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void shouldHaveCorrectCompensationFlagsInFullRollback() throws Exception {
        String sagaId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();

        String userCommandsTopic = randomTopic("user.commands");
        String merchantCommandsTopic = randomTopic("merchant.commands");

        SagaCommand<UnifiedSagaFixtures.DeductPaymentPayload> deduct = SagaCommand.of(
                sagaId, orderId, "DeductPaymentCommand", "user-service",
                new UnifiedSagaFixtures.DeductPaymentPayload("usr-001", orderId, new BigDecimal("299.99")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.ReserveInventoryPayload> reserve = SagaCommand.of(
                sagaId, orderId, "ReserveInventoryCommand", "merchant-service",
                new UnifiedSagaFixtures.ReserveInventoryPayload("SKU-001", orderId, 1),
                false
        );
        SagaCommand<UnifiedSagaFixtures.CreditMerchantPayload> credit = SagaCommand.of(
                sagaId, orderId, "CreditMerchantCommand", "merchant-service",
                new UnifiedSagaFixtures.CreditMerchantPayload("mrc-001", orderId, new BigDecimal("299.99")),
                false
        );
        SagaCommand<UnifiedSagaFixtures.ReleaseInventoryPayload> release = SagaCommand.of(
                sagaId, orderId, "ReleaseInventoryCommand", "merchant-service",
                new UnifiedSagaFixtures.ReleaseInventoryPayload("SKU-001", orderId, 1),
                true
        );
        SagaCommand<UnifiedSagaFixtures.RefundPaymentPayload> refund = SagaCommand.of(
                sagaId, orderId, "RefundPaymentCommand", "user-service",
                new UnifiedSagaFixtures.RefundPaymentPayload("usr-001", orderId, new BigDecimal("299.99")),
                true
        );

        publishMessages(userCommandsTopic, sagaId, List.of(deduct, refund));
        publishMessages(merchantCommandsTopic, sagaId, List.of(reserve, credit, release));

        List<JsonNode> userCommands = consumeMessages(userCommandsTopic, 2);
        List<JsonNode> merchantCommands = consumeMessages(merchantCommandsTopic, 3);

        assertThat(userCommands.get(0).get("compensation").asBoolean()).isFalse();
        assertThat(userCommands.get(1).get("compensation").asBoolean()).isTrue();
        assertThat(merchantCommands.get(0).get("compensation").asBoolean()).isFalse();
        assertThat(merchantCommands.get(1).get("compensation").asBoolean()).isFalse();
        assertThat(merchantCommands.get(2).get("compensation").asBoolean()).isTrue();
        assertThat(merchantCommands.get(0).get("commandType").asText()).isEqualTo("ReserveInventoryCommand");
        assertThat(merchantCommands.get(1).get("commandType").asText()).isEqualTo("CreditMerchantCommand");
        assertThat(merchantCommands.get(2).get("commandType").asText()).isEqualTo("ReleaseInventoryCommand");
    }
}
