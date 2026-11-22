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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OperationRepositoryAdapterTest {

    private static final String IDEMPOTENCY_KEY = "key-123";

    @Autowired
    private OperationRepository repository;

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

    private static PortabilityOperation operation() {
        return new PortabilityOperation("OPE-1", "BEN-123", List.of("INS-1", "INS-2"),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.PENDING, "PRT-1", null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
