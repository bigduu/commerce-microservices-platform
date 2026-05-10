package com.interview.order.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SagaInstanceTest {

    private static final String SAGA_ID = "saga-1";
    private static final String ORDER_ID = "order-1";

    @Test
    void constructor_shouldSetAllFields() {
        Instant timeoutAt = Instant.now().plusSeconds(300);

        SagaInstance saga = new SagaInstance(
                SAGA_ID, ORDER_ID, SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]", timeoutAt
        );

        assertEquals(SAGA_ID, saga.getSagaId());
        assertEquals(ORDER_ID, saga.getOrderId());
        assertEquals(SagaStep.DEDUCT_PAYMENT, saga.getCurrentStep());
        assertEquals(SagaStatus.RUNNING, saga.getStatus());
        assertEquals("[]", saga.getCompletedSteps());
        assertEquals(timeoutAt, saga.getTimeoutAt());
    }

    @Test
    void defaultConstructor_shouldCreateInstance() {
        SagaInstance saga = new SagaInstance();
        assertNotNull(saga);
    }

    @Test
    void settersAndGetters_shouldWork() {
        SagaInstance saga = new SagaInstance();
        Instant now = Instant.now();

        saga.setSagaId("s1");
        saga.setOrderId("o1");
        saga.setCurrentStep(SagaStep.RESERVE_INVENTORY);
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCompletedSteps("[\"DEDUCT_PAYMENT\"]");
        saga.setCreatedAt(now);
        saga.setUpdatedAt(now);
        saga.setTimeoutAt(now.plusSeconds(60));

        assertEquals("s1", saga.getSagaId());
        assertEquals("o1", saga.getOrderId());
        assertEquals(SagaStep.RESERVE_INVENTORY, saga.getCurrentStep());
        assertEquals(SagaStatus.COMPLETED, saga.getStatus());
        assertEquals("[\"DEDUCT_PAYMENT\"]", saga.getCompletedSteps());
        assertEquals(now, saga.getCreatedAt());
        assertEquals(now, saga.getUpdatedAt());
        assertEquals(now.plusSeconds(60), saga.getTimeoutAt());
    }

    @Test
    void prePersist_shouldSetCreatedAtAndUpdatedAt() {
        SagaInstance saga = new SagaInstance(
                SAGA_ID, ORDER_ID, SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]",
                Instant.now().plusSeconds(300)
        );

        saga.onCreate();

        assertNotNull(saga.getCreatedAt());
        assertNotNull(saga.getUpdatedAt());
        assertEquals(saga.getCreatedAt(), saga.getUpdatedAt());
    }

    @Test
    void preUpdate_shouldUpdateUpdatedAt() throws InterruptedException {
        SagaInstance saga = new SagaInstance(
                SAGA_ID, ORDER_ID, SagaStep.DEDUCT_PAYMENT, SagaStatus.RUNNING, "[]",
                Instant.now().plusSeconds(300)
        );
        saga.onCreate();

        Instant originalUpdatedAt = saga.getUpdatedAt();
        Thread.sleep(10);

        saga.setStatus(SagaStatus.COMPLETED);
        saga.onUpdate();

        assertNotNull(saga.getUpdatedAt());
        assert !saga.getUpdatedAt().equals(originalUpdatedAt) : "updatedAt should change after onUpdate";
    }
}
