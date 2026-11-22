package com.portability.domain.model;

import java.time.Instant;

public record Authorization(
        String authorizationId,
        BeneficiaryId beneficiaryId,
        AgreementId agreementId,
        Instant createdAt
) {
    public Authorization {
        Validation.requireNotBlank(authorizationId, "authorizationId");
        Validation.requireNonNull(beneficiaryId, "beneficiaryId");
        Validation.requireNonNull(agreementId, "agreementId");
        if (createdAt == null) {
            throw new InvalidRequestException("createdAt", "createdAt must not be null");
        }
    }
}
