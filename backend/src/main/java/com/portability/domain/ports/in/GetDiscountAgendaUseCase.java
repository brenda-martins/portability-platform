package com.portability.domain.ports.in;

import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.DiscountInstallment;

import java.util.List;

public interface GetDiscountAgendaUseCase {

    List<DiscountInstallment> getAgenda(BeneficiaryId beneficiaryId);
}
