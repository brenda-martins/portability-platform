package com.portability.domain.ports.out;

import com.portability.domain.model.DiscountInstallment;

import java.util.List;

public interface DiscountAgendaPort {

    List<DiscountInstallment> fetchAgenda(String beneficiaryId);
}
