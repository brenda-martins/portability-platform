package com.portability.domain.model;

public record AgreementId(String value) {

    public AgreementId {
        Validation.requireNotBlank(value, "agreementId");
    }
}
