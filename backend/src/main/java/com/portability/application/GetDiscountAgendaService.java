package com.portability.application;

import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.ports.in.GetDiscountAgendaUseCase;
import com.portability.domain.ports.out.AuthorizationRepository;
import com.portability.domain.ports.out.DiscountAgendaPort;
import com.portability.domain.ports.out.MetricsPort;

import java.util.List;

public class GetDiscountAgendaService implements GetDiscountAgendaUseCase {

    private final AuthorizationRepository authorizationRepository;
    private final DiscountAgendaPort discountAgendaPort;
    private final MetricsPort metrics;

    public GetDiscountAgendaService(
        final AuthorizationRepository authorizationRepository,
        final DiscountAgendaPort discountAgendaPort,
        final MetricsPort metrics
    ) {
        this.authorizationRepository = authorizationRepository;
        this.discountAgendaPort = discountAgendaPort;
        this.metrics = metrics;
    }

    @Override
    public List<DiscountInstallment> getAgenda(BeneficiaryId beneficiaryId) {
        if (!authorizationRepository.existsByBeneficiaryId(beneficiaryId)) {
            throw new BeneficiaryNotAuthorizedException("Beneficiary not authorized");
        }

        List<DiscountInstallment> agenda = discountAgendaPort.fetchAgenda(beneficiaryId);
        metrics.increment("agenda.consulted");
        return agenda;
    }
}
