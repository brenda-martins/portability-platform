package com.portability.domain.ports.in;

import com.portability.domain.model.DiscountInstallment;

import java.util.List;

public interface GetDiscountAgendaUseCase {

    List<DiscountInstallment> getAgenda(String beneficiaryId);
}
