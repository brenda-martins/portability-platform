package com.portability.domain.model;

public record BeneficiaryId(String value) {

    public BeneficiaryId {
        Validation.requireNotBlank(value, "beneficiaryId");
    }
}
