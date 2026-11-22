package com.portability.domain.model;

public class OperationNotConfirmedException extends DomainException {

    public OperationNotConfirmedException(String message) {
        super(message);
    }
}
