package com.portability.adapters.out.persistence;

import com.portability.domain.model.Authorization;
import com.portability.domain.ports.out.AuthorizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthorizationRepositoryAdapterTest {

    @Autowired
    private AuthorizationRepository repository;

    @Autowired
    private SpringDataAuthorizationRepository springRepository;

    @Test
    void shouldFindAuthorizationByBeneficiaryAndAgreement() {
        repository.save(authorization("AUT-1", "BEN-123", "AGR-001"));

        Optional<Authorization> found = repository.findByBeneficiaryAndAgreement("BEN-123", "AGR-001");
        assertTrue(found.isPresent());
        assertEquals("AUT-1", found.get().authorizationId());
        assertTrue(repository.existsByBeneficiaryId("BEN-123"));
    }

    @Test
    void shouldRejectDuplicatedBeneficiaryAndAgreementPair() {
        repository.save(authorization("AUT-1", "BEN-123", "AGR-001"));

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.save(authorization("AUT-2", "BEN-123", "AGR-001"));
            springRepository.flush();
        });
    }

    private static Authorization authorization(String authorizationId, String beneficiaryId,
            String agreementId) {
        return new Authorization(authorizationId, beneficiaryId, agreementId,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
