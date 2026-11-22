package com.portability.adapters.in.web.output;

import com.portability.domain.model.ReceivingAccount;

public record ReceivingAccountResponse(String bank, String agency, String account) {

    public static ReceivingAccountResponse from(ReceivingAccount account) {
        return new ReceivingAccountResponse(account.bank(), account.agency(), account.account());
    }
}
