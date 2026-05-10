package com.interview.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_saga_messages", indexes = {
        @Index(name = "idx_processed_command", columnList = "command_id")
})
public class ProcessedSagaMessage {

    @Id
    @Column(name = "command_id", nullable = false, length = 64)
    private String commandId;

    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "consumer_name", nullable = false, length = 64)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedSagaMessage() {
    }

    public ProcessedSagaMessage(String commandId, String sagaId, String orderId, String consumerName) {
        this.commandId = commandId;
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.consumerName = consumerName;
    }

    @PrePersist
    protected void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }

    public String getCommandId() {
        return commandId;
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getConsumerName() {
        return consumerName;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
