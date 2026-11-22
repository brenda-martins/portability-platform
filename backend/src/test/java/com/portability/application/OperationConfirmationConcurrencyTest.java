package com.portability.application;

import com.portability.adapters.out.persistence.SpringDataAuditEventRepository;
import com.portability.adapters.out.persistence.SpringDataOperationRepository;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.OperationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OperationConfirmationConcurrencyTest {

    private static final String OPERATION_ID = "OPE-CC-1";
    private static final String PROTOCOL = "PRT-CC-1";
    private static final String KEY = "key-cc-1";

    @Autowired
    private GetOperationStatusUseCase getOperationStatusUseCase;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private SpringDataOperationRepository springOperations;

    @Autowired
    private SpringDataAuditEventRepository springAuditEvents;

    @MockitoBean(name = "centralClient", extraInterfaces = {
            CentralAuthorizationPort.class, DiscountAgendaPort.class})
    private CentralPortabilityPort centralPortabilityPort;

    @AfterEach
    void cleanUp() {
        springAuditEvents.deleteAll();
        springOperations.deleteAll();
    }

    @Test
    void mustNotAppendTerminalEventTwiceWhenItIsAlreadyRecorded() {
        operationRepository.save(pendingOperation(), KEY);
        auditEventRepository.append(new AuditEvent(
                "EVT-CC-1", OPERATION_ID, "CONFIRMADA", "Evento anterior", Instant.now()));
        when(centralPortabilityPort.consultStatus(PROTOCOL))
                .thenReturn(new CentralOperationStatus(OperationStatus.CONFIRMED, null, null));

        PortabilityOperation result = getOperationStatusUseCase.getOperationStatus(OPERATION_ID).orElseThrow();

        assertEquals(OperationStatus.CONFIRMED, result.status());
        assertEquals(1, springAuditEvents.findByOperationIdOrderByOccurredAtAsc(OPERATION_ID).size());
    }

    @Test
    void mustAppendOnlyOneTerminalEventWhenTwoConsultationsRace() throws Exception {
        operationRepository.save(pendingOperation(), KEY);
        when(centralPortabilityPort.consultStatus(PROTOCOL))
                .thenReturn(new CentralOperationStatus(OperationStatus.CONFIRMED, null, null));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<java.util.Optional<PortabilityOperation>>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return getOperationStatusUseCase.getOperationStatus(OPERATION_ID);
                }));
            }
            start.countDown();

            for (Future<java.util.Optional<PortabilityOperation>> future : futures) {
                try {
                    assertTrue(future.get().isPresent());
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof ConcurrentOperationException
                            || exception.getCause() instanceof OptimisticLockingFailureException);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(OperationStatus.CONFIRMED,
                operationRepository.findById(OPERATION_ID).orElseThrow().status());
        assertEquals(1, springAuditEvents.findByOperationIdOrderByOccurredAtAsc(OPERATION_ID).size());
    }

    private static PortabilityOperation pendingOperation() {
        return new PortabilityOperation(
                OPERATION_ID, "BEN-CC-1", List.of("INS-CC-1"), "Nova Credora S.A.",
                new ReceivingAccount("001", "1234", "56789-0"), OperationStatus.PENDING, PROTOCOL, null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
