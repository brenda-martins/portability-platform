package com.portability.adapters.out.persistence;

import com.portability.domain.model.AuditEvent;
import com.portability.domain.ports.out.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("SOLICITADA", stored.get(0).getEventType());
        assertEquals("CONFIRMADA", stored.get(1).getEventType());
    }
}
