package com.portability.domain.model;

import java.time.Instant;

public record Authorization(
        String authorizationId,
        String beneficiaryId,
        String agreementId,
        Instant createdAt
) {
    public Authorization {
        Validation.requireNotBlank(authorizationId, "authorizationId");
        Validation.requireNotBlank(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(agreementId, "agreementId");
        if (createdAt == null) {
            throw new InvalidRequestException("createdAt", "createdAt must not be null");
        }
    }
}
