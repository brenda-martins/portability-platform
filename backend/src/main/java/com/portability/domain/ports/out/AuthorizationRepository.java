package com.portability.domain.ports.out;

import com.portability.domain.model.Authorization;

import java.util.Optional;

public interface AuthorizationRepository {

    Authorization save(Authorization authorization);

    Optional<Authorization> findByBeneficiaryAndAgreement(String beneficiaryId, String agreementId);
}
