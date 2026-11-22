package com.portability.domain.ports.out;

import com.portability.domain.model.AgreementId;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.BeneficiaryId;

import java.util.Optional;

public interface AuthorizationRepository {

    Authorization save(Authorization authorization);

    Optional<Authorization> findByBeneficiaryAndAgreement(BeneficiaryId beneficiaryId, AgreementId agreementId);

    boolean existsByBeneficiaryId(BeneficiaryId beneficiaryId);
}
