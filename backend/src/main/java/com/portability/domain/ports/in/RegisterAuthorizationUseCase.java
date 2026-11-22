package com.portability.domain.ports.in;

import com.portability.domain.model.AuthorizationResult;

public interface RegisterAuthorizationUseCase {

    AuthorizationResult registerAuthorization(String beneficiaryId, String agreementId);
}
