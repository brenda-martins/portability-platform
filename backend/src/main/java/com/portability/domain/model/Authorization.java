package com.portability.domain.model;

import java.time.Instant;

public record Authorization(
        String authorizationId,
        String beneficiaryId,
        String agreementId,
        Instant createdAt
) {
    public Authorization {
        requireNotBlank(beneficiaryId, "beneficiaryId");
        requireNotBlank(agreementId, "agreementId");
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
