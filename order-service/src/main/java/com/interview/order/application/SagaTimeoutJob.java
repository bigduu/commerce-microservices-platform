package com.interview.order.application;

import com.interview.order.domain.SagaInstance;
import com.interview.order.domain.SagaInstanceRepository;
import com.interview.order.domain.SagaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class SagaTimeoutJob {

    private static final Logger logger = LoggerFactory.getLogger(SagaTimeoutJob.class);

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public SagaTimeoutJob(SagaInstanceRepository sagaInstanceRepository,
                          SagaOrchestrator sagaOrchestrator) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void scanTimeoutSagas() {
        Instant now = Instant.now();
        handleTimeouts(sagaInstanceRepository.findByStatusInAndTimeoutAtBefore(
                List.of(SagaStatus.RUNNING, SagaStatus.COMPENSATING), now));
    }

    void handleTimeouts(List<SagaInstance> sagas) {
        for (SagaInstance saga : sagas) {
            logger.warn("Saga {} timed out at step {} with status {}",
                    saga.getSagaId(), saga.getCurrentStep(), saga.getStatus());
            sagaOrchestrator.handleTimeout(saga.getSagaId());
        }
    }
}
