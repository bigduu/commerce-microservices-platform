package com.interview.common.exception;

public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String aggregateId, Long version) {
        super("Optimistic lock conflict for aggregate " + aggregateId + " at version " + version);
    }
}
