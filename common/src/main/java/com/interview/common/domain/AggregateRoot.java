package com.interview.common.domain;

import com.interview.common.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot {

    private String aggregateId;
    private Long version = 0L;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private List<DomainEvent> unmodifiablePendingEvents = Collections.emptyList();

    protected AggregateRoot() {}

    protected AggregateRoot(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public Long getVersion() {
        return version;
    }

    protected void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public void loadFromEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            replay(event);
            this.version = event.getVersion();
        }
    }

    protected void apply(DomainEvent event) {
        this.version++;
        event.setAggregateId(this.aggregateId);
        event.setVersion(this.version);
        replay(event);
        pendingEvents.add(event);
        unmodifiablePendingEvents = Collections.unmodifiableList(pendingEvents);
    }

    protected abstract void replay(DomainEvent event);

    public List<DomainEvent> getPendingEvents() {
        return unmodifiablePendingEvents;
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
        unmodifiablePendingEvents = Collections.emptyList();
    }
}
