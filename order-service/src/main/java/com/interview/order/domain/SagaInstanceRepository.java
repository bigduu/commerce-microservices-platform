package com.interview.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    Optional<SagaInstance> findById(String sagaId);

    Optional<SagaInstance> findByOrderId(String orderId);

    List<SagaInstance> findByStatusAndTimeoutAtBefore(SagaStatus status, Instant timeout);

    List<SagaInstance> findByStatusInAndTimeoutAtBefore(List<SagaStatus> statuses, Instant timeout);
}
