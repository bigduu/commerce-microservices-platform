package com.interview.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedSagaMessageRepository extends JpaRepository<ProcessedSagaMessage, String> {

    boolean existsByCommandId(String commandId);
}
