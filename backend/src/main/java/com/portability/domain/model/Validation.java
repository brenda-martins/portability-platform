package com.portability.domain.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Validation {

    private Validation() {
    }

    public static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidRequestException(field, field + " must not be null");
        }
        return value;
    }

    public static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(field, field + " must not be blank");
        }
        return value;
    }

    public static ReceivingAccount requireAccount(ReceivingAccount account) {
        if (account == null) {
            throw new InvalidRequestException("receivingAccount", "receivingAccount must not be null");
        }
        return account;
    }

    public static List<String> requireSelection(List<String> installmentIds) {
        if (installmentIds == null || installmentIds.isEmpty()) {
            throw new InvalidRequestException("installmentIds", "installmentIds must not be empty");
        }
        if (installmentIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new InvalidRequestException("installmentIds", "installmentIds must not contain blank values");
        }
        if (new HashSet<>(installmentIds).size() != installmentIds.size()) {
            throw new InvalidRequestException("installmentIds", "installmentIds must not contain duplicates");
        }
        return List.copyOf(installmentIds);
    }

    public static void requireEligibleInstallments(
        List<String> installmentIds,
        List<DiscountInstallment> agenda, 
        LocalDate today
    ) {
        Map<String, DiscountInstallment> byId = agenda.stream()
                .collect(Collectors.toMap(DiscountInstallment::installmentId, Function.identity(),
                        (first, ignored) -> first));
        
        for(String installmentId : installmentIds) {
            DiscountInstallment installment = byId.get(installmentId);
            if (installment == null || installment.status() != InstallmentStatus.OPEN || !installment.discountDate().isAfter(today)) {
                throw new InvalidRequestException("installmentIds", "Ineligible installment: " + installmentId);
            }
        }
    }
}
