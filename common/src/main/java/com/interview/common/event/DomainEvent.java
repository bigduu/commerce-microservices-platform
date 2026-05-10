package com.interview.common.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    // Subtypes registered by each service
})
public abstract class DomainEvent {

    private String eventId;
    private String aggregateId;
    private String aggregateType;
    private String eventType;
    private Long version;
    private Instant occurredAt;
    private String sagaId;
    private String commandId;
    private String orderId;
    private String failureCode;
    private String failureReason;

    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.eventType = getClass().getSimpleName();
    }

    protected DomainEvent(String aggregateId, String aggregateType) {
        this();
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getCommandId() {
        return commandId;
    }

    public void setCommandId(String commandId) {
        this.commandId = commandId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public DomainEvent correlate(String sagaId, String commandId, String orderId) {
        this.sagaId = sagaId;
        this.commandId = commandId;
        if (orderId != null && !orderId.isBlank()) {
            setOrderId(orderId);
        }
        return this;
    }

    public DomainEvent fail(String failureCode, String failureReason) {
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        return this;
    }
}
