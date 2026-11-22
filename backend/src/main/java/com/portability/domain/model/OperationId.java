package com.portability.domain.model;

public record OperationId(String value) {

    public OperationId {
        Validation.requireNotBlank(value, "operationId");
    }

    @Override
    public String toString() {
        return value;
    }
}
