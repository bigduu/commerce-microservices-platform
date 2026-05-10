package com.interview.common.exception;

public class AggregateNotFoundException extends RuntimeException {
    public AggregateNotFoundException(String aggregateId) {
        super("Aggregate not found: " + aggregateId);
    }
}
