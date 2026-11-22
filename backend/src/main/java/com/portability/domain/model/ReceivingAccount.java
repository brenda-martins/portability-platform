package com.portability.domain.model;

/** Bank account that will receive the ported installments. */
public record ReceivingAccount(String bank, String agency, String account) {
}
