package com.portability.adapters.in.web.output;

import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentResponse(
        String installmentId,
        String agreementId,
        BigDecimal amount,
        LocalDate discountDate,
        InstallmentStatus status
) {
    public static InstallmentResponse from(DiscountInstallment installment) {
        return new InstallmentResponse(
                installment.installmentId(),
                installment.agreementId().value(),
                installment.amount(),
                installment.discountDate(),
                installment.status());
    }
}
