package com.portability.domain.model;

import java.time.Instant;

/** Opt-in authorization granted by a beneficiary for a given agreement. */
public record Authorization(
        String authorizationId,
        String beneficiaryId,
        String agreementId,
        Instant createdAt
) {
}
