package com.interview.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.interview.common.saga.SagaCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseFlowE2ETest extends KafkaSagaFlowTestSupport {

    @Test
    void successfulSagaFlow_shouldPublishUnifiedCommandsAndCorrelatedEvents() throws Exception {
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

        UnifiedSagaFixtures.PaymentDeducted paymentDeducted = new UnifiedSagaFixtures.PaymentDeducted(
                "usr-001", orderId, new BigDecimal("199.98")
        );
        paymentDeducted.correlate(sagaId, deduct.commandId(), orderId);

        UnifiedSagaFixtures.InventoryReserved inventoryReserved = new UnifiedSagaFixtures.InventoryReserved(
                orderId, "SKU-001", 2
        );
        inventoryReserved.correlate(sagaId, reserve.commandId(), orderId);

        UnifiedSagaFixtures.MerchantCredited merchantCredited = new UnifiedSagaFixtures.MerchantCredited(
                orderId, "mrc-001", new BigDecimal("199.98")
        );
        merchantCredited.correlate(sagaId, credit.commandId(), orderId);

        publishMessage(userCommandsTopic, sagaId, deduct);
        publishMessages(merchantCommandsTopic, sagaId, List.of(reserve, credit));
        publishMessage(userEventsTopic, sagaId, paymentDeducted);
        publishMessages(merchantEventsTopic, sagaId, List.of(inventoryReserved, merchantCredited));

        List<JsonNode> userCommands = consumeMessages(userCommandsTopic, 1);
        List<JsonNode> merchantCommands = consumeMessages(merchantCommandsTopic, 2);
        List<JsonNode> userEvents = consumeMessages(userEventsTopic, 1);
        List<JsonNode> merchantEvents = consumeMessages(merchantEventsTopic, 2);

        JsonNode deductJson = userCommands.get(0);
        assertThat(deductJson.get("commandType").asText()).isEqualTo("DeductPaymentCommand");
        assertThat(deductJson.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(deductJson.get("orderId").asText()).isEqualTo(orderId);
        assertThat(deductJson.get("compensation").asBoolean()).isFalse();
        assertThat(deductJson.path("payload").path("userId").asText()).isEqualTo("usr-001");
        assertThat(deductJson.path("payload").path("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));

        assertThat(merchantCommands)
                .extracting(node -> node.get("commandType").asText())
                .containsExactly("ReserveInventoryCommand", "CreditMerchantCommand");
        assertThat(merchantCommands)
                .extracting(node -> node.get("targetService").asText())
                .containsOnly("merchant-service");
        assertThat(merchantCommands.get(0).path("payload").path("sku").asText()).isEqualTo("SKU-001");
        assertThat(merchantCommands.get(1).path("payload").path("merchantId").asText()).isEqualTo("mrc-001");

        JsonNode paymentEventJson = userEvents.get(0);
        assertThat(paymentEventJson.get("eventType").asText()).isEqualTo("PaymentDeducted");
        assertThat(paymentEventJson.get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(paymentEventJson.get("commandId").asText()).isEqualTo(deduct.commandId());
        assertThat(paymentEventJson.get("orderId").asText()).isEqualTo(orderId);

        assertThat(merchantEvents)
                .extracting(node -> node.get("eventType").asText())
                .containsExactly("InventoryReserved", "MerchantCredited");
        assertThat(merchantEvents.get(0).get("commandId").asText()).isEqualTo(reserve.commandId());
        assertThat(merchantEvents.get(0).get("sagaId").asText()).isEqualTo(sagaId);
        assertThat(merchantEvents.get(0).get("sku").asText()).isEqualTo("SKU-001");
        assertThat(merchantEvents.get(1).get("commandId").asText()).isEqualTo(credit.commandId());
        assertThat(merchantEvents.get(1).get("merchantId").asText()).isEqualTo("mrc-001");
        assertThat(merchantEvents.get(1).get("amount").decimalValue())
                .isEqualByComparingTo(new BigDecimal("199.98"));
    }
}
