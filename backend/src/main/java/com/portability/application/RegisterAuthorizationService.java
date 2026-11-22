package com.portability.application;

import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.Validation;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.MetricsPort;
import com.portability.domain.ports.out.TransactionPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class RegisterAuthorizationService implements RegisterAuthorizationUseCase {

    private final AuthorizationRepository authorizationRepository;
    private final CentralAuthorizationPort centralAuthorizationPort;
    private final TransactionPort transactions;
    private final Clock clock;
    private final MetricsPort metrics;

    public RegisterAuthorizationService(
        final AuthorizationRepository authorizationRepository,
        final CentralAuthorizationPort centralAuthorizationPort, 
        final TransactionPort transactions,
        final Clock clock, 
        final MetricsPort metrics
    ) {
        this.authorizationRepository = authorizationRepository;
        this.centralAuthorizationPort = centralAuthorizationPort;
        this.transactions = transactions;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Override
    public AuthorizationResult registerAuthorization(String beneficiaryId, String agreementId) {
        Validation.requireNotBlank(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(agreementId, "agreementId");
        
        return transactions.inTransaction(() -> {
            Optional<Authorization> existing = authorizationRepository
                    .findByBeneficiaryAndAgreement(beneficiaryId, agreementId);
            if (existing.isPresent()) {
                metrics.increment("authorization.resend");
                return new AuthorizationResult(existing.get(), false);
            }
            String authorizationId = centralAuthorizationPort.registerAuthorization(beneficiaryId, agreementId);
            Authorization authorization = new Authorization(authorizationId, beneficiaryId, agreementId,
                    Instant.now(clock));
            Authorization saved = authorizationRepository.save(authorization);
            metrics.increment("authorization.created");
            return new AuthorizationResult(saved, true);
        });
    }
}
