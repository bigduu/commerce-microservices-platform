package com.interview.common.saga;

import java.time.Instant;
import java.util.UUID;

public record SagaCommand<T>(
        String commandId,
        String sagaId,
        String orderId,
        String commandType,
        String targetService,
        Instant createdAt,
        T payload,
        boolean compensation
) {

    public SagaCommand {
        if (commandId == null || commandId.isBlank()) {
            commandId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public static <T> SagaCommand<T> of(String sagaId,
                                        String orderId,
                                        String commandType,
                                        String targetService,
                                        T payload,
                                        boolean compensation) {
        return new SagaCommand<>(
                UUID.randomUUID().toString(),
                sagaId,
                orderId,
                commandType,
                targetService,
                Instant.now(),
                payload,
                compensation
        );
    }
}
