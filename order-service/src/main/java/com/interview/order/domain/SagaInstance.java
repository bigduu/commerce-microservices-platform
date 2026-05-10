package com.interview.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saga_instances", indexes = {
        @Index(name = "idx_saga_status_timeout", columnList = "status, timeout_at")
})
public class SagaInstance {

    @Id
    @Column(name = "saga_id")
    private String sagaId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 32)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SagaStatus status;

    @Column(name = "completed_steps", length = 512)
    private String completedSteps;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "timeout_at", nullable = false)
    private Instant timeoutAt;

    public SagaInstance() {
    }

    public SagaInstance(String sagaId, String orderId, SagaStep currentStep, SagaStatus status,
                        String completedSteps, Instant timeoutAt) {
        this.sagaId = sagaId;
        this.orderId = orderId;
        this.currentStep = currentStep;
        this.status = status;
        this.completedSteps = completedSteps;
        this.timeoutAt = timeoutAt;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public SagaStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(SagaStep currentStep) {
        this.currentStep = currentStep;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStatus status) {
        this.status = status;
    }

    public String getCompletedSteps() {
        return completedSteps;
    }

    public void setCompletedSteps(String completedSteps) {
        this.completedSteps = completedSteps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getTimeoutAt() {
        return timeoutAt;
    }

    public void setTimeoutAt(Instant timeoutAt) {
        this.timeoutAt = timeoutAt;
    }
}
