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
        // TODO: map to/from AuthorizationJpaEntity and persist.
        throw new UnsupportedOperationException("TODO: save authorization");
    }

    @Override
    public Optional<Authorization> findByBeneficiaryAndAgreement(String beneficiaryId, String agreementId) {
        // TODO: query and map to the domain model.
        throw new UnsupportedOperationException("TODO: find authorization");
    }
}
