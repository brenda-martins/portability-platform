package com.portability.domain.model;

public enum AuditEventType {
    REQUESTED("SOLICITADA"),
    CONFIRMED("CONFIRMADA"),
    REJECTED("REJEITADA"),
    INTEGRATION_FAILURE("FALHA_INTEGRACAO"),
    ACCOUNT_UPDATED("CONTA_ATUALIZADA");

    private final String externalValue;

    AuditEventType(String externalValue) {
        this.externalValue = externalValue;
    }

    public String externalValue() {
        return externalValue;
    }
}
