package com.portability.domain.ports.in;

import com.portability.domain.model.Authorization;

public interface RegisterAuthorizationUseCase {

    Authorization registerAuthorization(String beneficiaryId, String agreementId);
}
