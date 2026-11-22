package com.portability.adapters.in.web.input;

import com.portability.domain.model.ReceivingAccount;

public record ReceivingAccountRequest(String bank, String agency, String account) {

    public void validate() {
        toAccount();
    }

    public ReceivingAccount toAccount() {
        return new ReceivingAccount(bank, agency, account);
    }
}
