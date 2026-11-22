package com.portability.adapters.out.persistence;

import com.portability.domain.model.Authorization;
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
        entity.setBeneficiaryId(authorization.beneficiaryId());
        entity.setAgreementId(authorization.agreementId());
        entity.setCreatedAt(authorization.createdAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Authorization> findByBeneficiaryAndAgreement(String beneficiaryId, String agreementId) {
        return repository.findByBeneficiaryIdAndAgreementId(beneficiaryId, agreementId)
                .map(AuthorizationRepositoryAdapter::toDomain);
    }

    private static Authorization toDomain(AuthorizationJpaEntity entity) {
        return new Authorization(
                entity.getAuthorizationId(),
                entity.getBeneficiaryId(),
                entity.getAgreementId(),
                entity.getCreatedAt());
    }
}
