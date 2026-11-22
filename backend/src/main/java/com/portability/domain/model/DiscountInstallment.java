package com.portability.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A future payroll discount installment from the beneficiary agenda. */
public record DiscountInstallment(
        String installmentId,
        String agreementId,
        BigDecimal amount,
        LocalDate discountDate,
        InstallmentStatus status
) {
}
