package com.portability.application;

import com.portability.adapters.out.persistence.SpringDataAuditEventRepository;
import com.portability.adapters.out.persistence.SpringDataIdempotencyReservationRepository;
import com.portability.adapters.out.persistence.SpringDataOperationRepository;
import com.portability.adapters.out.persistence.SpringDataAuthorizationRepository;
import com.portability.domain.model.Authorization;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class StartPortabilityIdempotencyConcurrencyTest {

    @Autowired
    private StartPortabilityUseCase startPortabilityUseCase;

    @Autowired
    private AuthorizationRepository authorizationRepository;

    @Autowired
    private SpringDataOperationRepository springOperations;

    @Autowired
    private SpringDataAuditEventRepository springAuditEvents;

    @Autowired
    private SpringDataIdempotencyReservationRepository springReservations;

    @Autowired
    private SpringDataAuthorizationRepository springAuthorizations;

    @MockitoBean(name = "centralClient", extraInterfaces = {
            CentralAuthorizationPort.class, DiscountAgendaPort.class})
    private CentralPortabilityPort centralClient;

    @AfterEach
    void cleanUp() {
        springAuditEvents.deleteAll();
        springOperations.deleteAll();
        springReservations.deleteAll();
        springAuthorizations.deleteAll();
    }

    @Test
    void mustRegisterOnlyOneOperationWhenIdenticalRequestsRace() throws Exception {
        authorizationRepository.save(new Authorization(
                "AUT-CC-1", "BEN-123", "AGR-001", Instant.parse("2026-11-22T12:34:56Z")));
        when(((DiscountAgendaPort) centralClient).fetchAgenda("BEN-123")).thenReturn(List.of(new DiscountInstallment(
                "INS-1", "AGR-001", new BigDecimal("120.50"),
                LocalDate.parse("2099-01-10"), InstallmentStatus.OPEN)));
        when(centralClient.registerPortability(anyString(), anyList(), anyString(), any()))
                .thenReturn("PRT-CC-1");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<PortabilityOperationResult>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return startPortabilityUseCase.startPortability("BEN-123", List.of("INS-1"),
                            "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"));
                }));
            }
            start.countDown();

            List<PortabilityOperationResult> results = new ArrayList<>();
            for (Future<PortabilityOperationResult> future : futures) {
                results.add(future.get());
            }

            assertEquals(1, results.stream().filter(PortabilityOperationResult::created).count());
            assertEquals(1, results.stream().filter(result -> !result.created()).count());
            assertEquals(results.get(0).operation().operationId(), results.get(1).operation().operationId());
        } finally {
            executor.shutdownNow();
        }

        verify(centralClient, times(1)).registerPortability(anyString(), anyList(), anyString(), any());
        assertEquals(1, springOperations.count());
        assertEquals(1, springAuditEvents.count());
        assertEquals(1, springReservations.count());
    }
}
