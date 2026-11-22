package com.portability.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountInstallment(
        String installmentId,
        String agreementId,
        BigDecimal amount,
        LocalDate discountDate,
        InstallmentStatus status
) {
    public DiscountInstallment {
        Validation.requireNotBlank(installmentId, "installmentId");
        Validation.requireNotBlank(agreementId, "agreementId");
        if (amount == null) {
            throw new InvalidRequestException("amount", "amount must not be null");
        }
        if (discountDate == null) {
            throw new InvalidRequestException("discountDate", "discountDate must not be null");
        }
        if (status == null) {
            throw new InvalidRequestException("status", "status must not be null");
        }
    }
}
