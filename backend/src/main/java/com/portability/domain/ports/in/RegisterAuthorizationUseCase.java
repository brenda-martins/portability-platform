package com.portability.domain.ports.in;

import com.portability.domain.model.AgreementId;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryId;

public interface RegisterAuthorizationUseCase {

    AuthorizationResult registerAuthorization(BeneficiaryId beneficiaryId, AgreementId agreementId);
}
