package com.portability.domain.ports.out;

import com.portability.domain.model.AgreementId;
import com.portability.domain.model.BeneficiaryId;

public interface CentralAuthorizationPort {

    String registerAuthorization(BeneficiaryId beneficiaryId, AgreementId agreementId);
}
