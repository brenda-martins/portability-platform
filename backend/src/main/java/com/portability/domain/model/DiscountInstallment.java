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
        requireNotBlank(installmentId, "installmentId");
        requireNotBlank(agreementId, "agreementId");
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (discountDate == null) {
            throw new IllegalArgumentException("discountDate must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    private static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
