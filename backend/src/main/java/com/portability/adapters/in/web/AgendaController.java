package com.portability.adapters.in.web;

import com.portability.adapters.in.web.output.InstallmentResponse;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.ports.in.GetDiscountAgendaUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
public class AgendaController {

    private final GetDiscountAgendaUseCase getDiscountAgendaUseCase;

    public AgendaController(final GetDiscountAgendaUseCase getDiscountAgendaUseCase) {
        this.getDiscountAgendaUseCase = getDiscountAgendaUseCase;
    }

    @GetMapping("/{beneficiaryId}/agenda")
    public List<InstallmentResponse> getAgenda(@PathVariable String beneficiaryId) {
        return getDiscountAgendaUseCase.getAgenda(new BeneficiaryId(beneficiaryId)).stream()
                .map(InstallmentResponse::from)
                .toList();
    }
}
