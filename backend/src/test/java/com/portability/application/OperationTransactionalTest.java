package com.portability.application;

import com.portability.adapters.out.persistence.SpringDataOperationRepository;
import com.portability.domain.model.AgreementId;
import com.portability.domain.model.AuditEvent;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.StartPortabilityCommand;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.out.AuditEventRepository;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.OperationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OperationTransactionalTest {

    private static final String BENEFICIARY_ID = "BEN-TX-1";
    private static final String AGREEMENT_ID = "AGR-TX-1";
    private static final String INSTALLMENT_ID = "INS-TX-1";
    private static final String OPERATION_ID = "OPE-TX-1";
    private static final String PROTOCOL = "PRT-TX-1";
    private static final String KEY = "key-tx-1";

    @Autowired
    private StartPortabilityUseCase startPortabilityUseCase;

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private SpringDataOperationRepository springOperations;

    @MockitoBean
    private AuthorizationRepository authorizationRepository;

    @MockitoBean(name = "centralClient", extraInterfaces = {
            CentralAuthorizationPort.class, DiscountAgendaPort.class})
    private CentralPortabilityPort centralPortabilityPort;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @AfterEach
    void cleanUp() {
        springOperations.deleteAll();
    }

    @Test
    void shouldRollbackOperationWhenAuditAppendFails() {
        when(authorizationRepository.existsByBeneficiaryId(new BeneficiaryId(BENEFICIARY_ID))).thenReturn(true);
        when(((DiscountAgendaPort) centralPortabilityPort).fetchAgenda(new BeneficiaryId(BENEFICIARY_ID)))
                .thenReturn(List.of(new DiscountInstallment(INSTALLMENT_ID, new AgreementId(AGREEMENT_ID),
                        new BigDecimal("120.50"), LocalDate.now().plusDays(10), InstallmentStatus.OPEN)));
        when(centralPortabilityPort.registerPortability(any(StartPortabilityCommand.class)))
                .thenReturn(new Protocol(PROTOCOL));
        when(auditEventRepository.append(any(AuditEvent.class)))
                .thenThrow(new IllegalStateException("audit unavailable"));

        assertThrows(IllegalStateException.class,
                () -> startPortabilityUseCase.startPortability(command()));

        assertEquals(0, springOperations.count());
    }

    @Test
    void shouldRejectStaleUpdateWithOptimisticLock() {
        PortabilityOperation saved = operationRepository.save(pendingOperation(), KEY);
        PortabilityOperation current = saved.transitionTo(OperationStatus.CONFIRMED, null);
        PortabilityOperation stale = saved.transitionTo(OperationStatus.REJECTED, "Agreement blocked");

        operationRepository.save(current);

        assertThrows(ConcurrentOperationException.class, () -> operationRepository.save(stale));
        assertEquals(OperationStatus.CONFIRMED,
                operationRepository.findById(new OperationId(OPERATION_ID)).orElseThrow().status());
    }

    private static StartPortabilityCommand command() {
        return new StartPortabilityCommand(new BeneficiaryId(BENEFICIARY_ID), List.of(INSTALLMENT_ID),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"));
    }

    private static PortabilityOperation pendingOperation() {
        return new PortabilityOperation(
                new OperationId(OPERATION_ID), new BeneficiaryId(BENEFICIARY_ID), List.of(INSTALLMENT_ID),
                "Nova Credora S.A.", new ReceivingAccount("001", "1234", "56789-0"),
                OperationStatus.PENDING, new Protocol(PROTOCOL), null,
                Instant.parse("2026-11-22T12:34:56Z"));
    }
}
