package com.portability.adapters.out.persistence;

import com.portability.adapters.out.persistence.entity.AuthorizationJpaEntity;
import com.portability.domain.model.AgreementId;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.ports.out.AuthorizationRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuthorizationRepositoryAdapter implements AuthorizationRepository {

    private final SpringDataAuthorizationRepository repository;

    public AuthorizationRepositoryAdapter(SpringDataAuthorizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Authorization save(Authorization authorization) {
        AuthorizationJpaEntity entity = new AuthorizationJpaEntity();
        entity.setAuthorizationId(authorization.authorizationId());
        entity.setBeneficiaryId(authorization.beneficiaryId().value());
        entity.setAgreementId(authorization.agreementId().value());
        entity.setCreatedAt(authorization.createdAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Authorization> findByBeneficiaryAndAgreement(
            BeneficiaryId beneficiaryId, AgreementId agreementId) {
        return repository.findByBeneficiaryIdAndAgreementId(beneficiaryId.value(), agreementId.value())
                .map(AuthorizationRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByBeneficiaryId(BeneficiaryId beneficiaryId) {
        return repository.existsByBeneficiaryId(beneficiaryId.value());
    }

    private static Authorization toDomain(AuthorizationJpaEntity entity) {
        return new Authorization(
                entity.getAuthorizationId(),
                new BeneficiaryId(entity.getBeneficiaryId()),
                new AgreementId(entity.getAgreementId()),
                entity.getCreatedAt());
    }
}
