package com.portability.domain.model;

/** Raised when the receiving account of a non-confirmed operation is updated. */
public class OperationNotConfirmedException extends RuntimeException {

    public OperationNotConfirmedException(String message) {
        super(message);
    }
}
