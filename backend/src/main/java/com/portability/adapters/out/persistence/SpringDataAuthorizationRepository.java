package com.portability.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataAuthorizationRepository extends JpaRepository<AuthorizationJpaEntity, String> {

    Optional<AuthorizationJpaEntity> findByBeneficiaryIdAndAgreementId(String beneficiaryId, String agreementId);
}
