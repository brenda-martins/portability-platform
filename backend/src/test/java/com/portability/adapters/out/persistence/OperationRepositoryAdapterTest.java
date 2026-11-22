package com.portability.adapters.out.persistence;

import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.OperationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OperationRepositoryAdapterTest {

    private static final String IDEMPOTENCY_KEY = "key-123";

    @Autowired
    private OperationRepository repository;

    @Autowired
    private SpringDataOperationRepository springRepository;

    @Test
    void shouldSaveAndFindOperationByIdempotencyKey() {
        PortabilityOperation operation = operation();

        repository.save(operation, IDEMPOTENCY_KEY);

        Optional<PortabilityOperation> found = repository.findByIdempotencyKey(IDEMPOTENCY_KEY);
        assertTrue(found.isPresent());
        assertEquals(operation.operationId(), found.get().operationId());
        assertEquals(operation.beneficiaryId(), found.get().beneficiaryId());
        assertEquals(operation.installmentIds(), found.get().installmentIds());
        assertEquals(operation.creditorAssignee(), found.get().creditorAssignee());
        assertEquals(operation.receivingAccount(), found.get().receivingAccount());
        assertEquals(OperationStatus.PENDING, found.get().status());
        assertEquals(operation.protocol(), found.get().protocol());
        assertEquals(operation.requestedAt(), found.get().requestedAt());
    }

    @Test
    void shouldReturnEmptyWhenIdempotencyKeyIsUnknown() {
        assertTrue(repository.findByIdempotencyKey("unknown").isEmpty());
    }

    @Test
    void shouldFindOperationByIdAndProtocol() {
        repository.save(operation(), IDEMPOTENCY_KEY);

        Optional<PortabilityOperation> byId = repository.findById("OPE-1");
        Optional<PortabilityOperation> byProtocol = repository.findByProtocol("PRT-1");

        assertTrue(byId.isPresent());
        assertTrue(byProtocol.isPresent());
        assertEquals("OPE-1", byId.get().operationId());
        assertEquals("OPE-1", byProtocol.get().operationId());
        assertEquals(List.of("INS-1", "INS-2"), byProtocol.get().installmentIds());
    }

    @Test
    void shouldUpdateOnlyStatusAndRejectionReason() {
        PortabilityOperation saved = repository.save(operation(), IDEMPOTENCY_KEY);

        PortabilityOperation updated = repository.updateStatus("OPE-1", OperationStatus.CONFIRMED, null);

        assertEquals(OperationStatus.CONFIRMED, updated.status());
        assertEquals(saved.operationId(), updated.operationId());
        assertEquals(saved.installmentIds(), updated.installmentIds());
        assertEquals(saved.receivingAccount(), updated.receivingAccount());
        assertEquals(saved.requestedAt(), updated.requestedAt());
        assertEquals(IDEMPOTENCY_KEY, springRepository.findById("OPE-1").orElseThrow().getIdempotencyKey());
    }

    @Test
    void shouldUpdateOnlyReceivingAccount() {
        PortabilityOperation saved = repository.save(operation(), IDEMPOTENCY_KEY);
        ReceivingAccount newAccount = new ReceivingAccount("002", "4321", "98765-4");

        PortabilityOperation updated = repository.updateReceivingAccount("OPE-1", newAccount);

        assertEquals(newAccount, updated.receivingAccount());
        assertEquals(saved.operationId(), updated.operationId());
        assertEquals(saved.beneficiaryId(), updated.beneficiaryId());
        assertEquals(saved.installmentIds(), updated.installmentIds());
        assertEquals(saved.creditorAssignee(), updated.creditorAssignee());
        assertEquals(saved.status(), updated.status());
        assertEquals(saved.protocol(), updated.protocol());
        assertEquals(saved.rejectionReason(), updated.rejectionReason());
        assertEquals(saved.requestedAt(), updated.requestedAt());
        assertEquals(IDEMPOTENCY_KEY, springRepository.findById("OPE-1").orElseThrow().getIdempotencyKey());
    }

    private static PortabilityOperation operation() {
        return new PortabilityOperation("OPE-1", "BEN-123", List.of("INS-1", "INS-2"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.PENDING, "PRT-1", null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
