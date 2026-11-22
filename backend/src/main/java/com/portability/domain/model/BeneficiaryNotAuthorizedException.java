package com.portability.domain.model;

/** Raised when a beneficiary tries to read an agenda without a registered opt-in. */
public class BeneficiaryNotAuthorizedException extends RuntimeException {

    public BeneficiaryNotAuthorizedException(String message) {
        super(message);
    }
}
