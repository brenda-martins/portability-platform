package com.portability.domain.model;

public class DuplicateIdempotencyKeyException extends DomainException {

    public DuplicateIdempotencyKeyException(String message) {
        super(message);
    }

    public DuplicateIdempotencyKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
