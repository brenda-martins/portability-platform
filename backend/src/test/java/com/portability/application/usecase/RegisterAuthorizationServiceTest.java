package com.portability.application.usecase;

import com.portability.application.RegisterAuthorizationService;
import com.portability.domain.model.AgreementId;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterAuthorizationServiceTest {

    private static final BeneficiaryId BENEFICIARY = new BeneficiaryId("BEN-123");
    private static final AgreementId AGREEMENT = new AgreementId("AGR-001");
    private static final String CREATED_AT = "2026-11-22T12:34:56Z";

    private TestDoubles.State state;
    private TestDoubles.Authorizations authorizations;
    private TestDoubles.CentralAuthorization central;
    private TestDoubles.AuditEvents auditEvents;
    private TestDoubles.Metrics metrics;
    private RegisterAuthorizationService service;

    @BeforeEach
    void setUp() {
        state = new TestDoubles.State();
        authorizations = new TestDoubles.Authorizations(state);
        central = new TestDoubles.CentralAuthorization();
        auditEvents = new TestDoubles.AuditEvents(state);
        metrics = new TestDoubles.Metrics();
        service = new RegisterAuthorizationService(authorizations, central,
                new TestDoubles.Transactions(state), TestDoubles.fixedClock(CREATED_AT), metrics);
    }

    @Test
    void mustRegisterAndPersistAuthorizationWhenPairIsNew() {
        AuthorizationResult result = service.registerAuthorization(BENEFICIARY, AGREEMENT);

        assertTrue(result.created());
        assertEquals("AUT-1", result.authorization().authorizationId());
        assertEquals(BENEFICIARY, result.authorization().beneficiaryId());
        assertEquals(AGREEMENT, result.authorization().agreementId());
        assertEquals(Instant.parse(CREATED_AT), result.authorization().createdAt());
        assertTrue(metrics.names.contains("authorization.created"));
    }

    @Test
    void mustReuseExistingAuthorizationWithoutCallingCentralWhenResent() {
        Authorization existing = new Authorization("AUT-OLD", BENEFICIARY, AGREEMENT,
                Instant.parse("2026-01-01T00:00:00Z"));
        authorizations.save(existing);

        AuthorizationResult result = service.registerAuthorization(BENEFICIARY, AGREEMENT);

        assertFalse(result.created());
        assertEquals(existing, result.authorization());
        assertEquals(1, state.authorizations.size());
        assertTrue(metrics.names.contains("authorization.resend"));
    }

    @Test
    void mustRejectBlankBeneficiaryBeforeTouchingPorts() {
        InvalidRequestException error = assertThrows(InvalidRequestException.class,
                () -> new BeneficiaryId(" "));

        assertEquals("beneficiaryId must not be blank", error.fields().get("beneficiaryId"));
        assertTrue(state.authorizations.isEmpty());
        assertTrue(metrics.names.isEmpty());
    }

    @Test
    void mustNotPersistAuthorizationWhenCentralFails() {
        central.failure = TestDoubles.centralFailure("Central unavailable");

        assertThrows(CentralUnavailableException.class,
                () -> service.registerAuthorization(BENEFICIARY, AGREEMENT));

        assertTrue(state.authorizations.isEmpty());
        assertTrue(auditEvents.findByOperationId(new com.portability.domain.model.OperationId("OPE-1")).isEmpty());
    }
}
