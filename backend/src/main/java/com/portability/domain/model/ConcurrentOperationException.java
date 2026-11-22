package com.portability.domain.model;

public class ConcurrentOperationException extends DomainException {

    public ConcurrentOperationException(String message) {
        super(message);
    }

    public ConcurrentOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
