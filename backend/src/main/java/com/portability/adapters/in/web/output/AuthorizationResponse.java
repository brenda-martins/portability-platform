package com.portability.adapters.in.web.output;

import com.portability.domain.model.Authorization;

import java.time.Instant;

public record AuthorizationResponse(
        String authorizationId,
        String beneficiaryId,
        String agreementId,
        Instant createdAt
) {
    public static AuthorizationResponse from(Authorization authorization) {
        return new AuthorizationResponse(
                authorization.authorizationId(),
                authorization.beneficiaryId().value(),
                authorization.agreementId().value(),
                authorization.createdAt());
    }
}
