package com.portability.domain.model;

public record ReceivingAccount(String bank, String agency, String account) {

    public ReceivingAccount {
        Validation.requireNotBlank(bank, "bank");
        Validation.requireNotBlank(agency, "agency");
        Validation.requireNotBlank(account, "account");
    }
}
