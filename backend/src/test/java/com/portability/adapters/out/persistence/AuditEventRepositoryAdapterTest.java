package com.portability.adapters.out.persistence;

import com.portability.adapters.out.persistence.entity.AuditEventJpaEntity;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.ports.out.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditEventRepositoryAdapterTest {

    @Autowired
    private AuditEventRepository repository;

    @Autowired
    private SpringDataAuditEventRepository springRepository;

    @Test
    void shouldAppendEventWithoutOverwritingPreviousOnes() {
        Instant occurredAt = Instant.parse("2026-11-22T12:34:56Z");
        repository.append(new AuditEvent("EVT-1", "OPE-1", "SOLICITADA", "Protocolo PRT-1", occurredAt));
        repository.append(new AuditEvent("EVT-2", "OPE-1", "CONFIRMADA", "Protocolo PRT-1", occurredAt));

        List<AuditEventJpaEntity> stored =
                springRepository.findByOperationIdOrderByOccurredAtAsc("OPE-1");

        assertEquals(2, stored.size());
        assertEquals(Set.of("SOLICITADA", "CONFIRMADA"), stored.stream()
                .map(AuditEventJpaEntity::getEventType)
                .collect(Collectors.toSet()));
        assertTrue(stored.stream().allMatch(event -> occurredAt.equals(event.getOccurredAt())));
    }

    @Test
    void shouldFindOnlyEventsOfTheRequestedOperationOrderedByOccurrence() {
        repository.append(new AuditEvent("EVT-LATE", "OPE-1", "CONFIRMADA", "Confirmada",
                Instant.parse("2026-11-22T12:35:56Z")));
        repository.append(new AuditEvent("EVT-EARLY", "OPE-1", "SOLICITADA", "Solicitada",
                Instant.parse("2026-11-22T12:34:56Z")));
        repository.append(new AuditEvent("EVT-OTHER", "OPE-2", "SOLICITADA", "Outra operação",
                Instant.parse("2026-11-22T12:34:00Z")));

        List<AuditEvent> events = repository.findByOperationId("OPE-1");

        assertEquals(2, events.size());
        assertEquals("EVT-EARLY", events.get(0).eventId());
        assertEquals("SOLICITADA", events.get(0).eventType());
        assertEquals("Solicitada", events.get(0).message());
        assertEquals("OPE-1", events.get(0).operationId());
        assertEquals(Instant.parse("2026-11-22T12:34:56Z"), events.get(0).occurredAt());
        assertEquals("EVT-LATE", events.get(1).eventId());
        assertEquals("CONFIRMADA", events.get(1).eventType());
    }
}
