package com.interview.order.application;

import com.interview.order.domain.SagaInstance;
import com.interview.order.domain.SagaInstanceRepository;
import com.interview.order.domain.SagaStatus;
import com.interview.order.domain.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaTimeoutJobTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    private SagaTimeoutJob sagaTimeoutJob;

    @BeforeEach
    void setUp() {
        sagaTimeoutJob = new SagaTimeoutJob(sagaInstanceRepository, sagaOrchestrator);
    }

    @Test
    void scanTimeoutSagas_shouldDelegateTimedOutRunningAndCompensatingSagas() {
        Instant timeout = Instant.now().minusSeconds(5);
        SagaInstance running = new SagaInstance("saga-running", "order-1", SagaStep.RESERVE_INVENTORY, SagaStatus.RUNNING, "[]", timeout);
        SagaInstance compensating = new SagaInstance("saga-comp", "order-2", SagaStep.CREDIT_MERCHANT, SagaStatus.COMPENSATING, "[]", timeout);

        when(sagaInstanceRepository.findByStatusInAndTimeoutAtBefore(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(running, compensating));

        sagaTimeoutJob.scanTimeoutSagas();

        verify(sagaOrchestrator).handleTimeout("saga-running");
        verify(sagaOrchestrator).handleTimeout("saga-comp");
    }
}
