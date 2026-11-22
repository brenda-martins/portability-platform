package com.portability.domain.model;

public class BeneficiaryNotAuthorizedException extends DomainException {

    public BeneficiaryNotAuthorizedException(String message) {
        super(message);
    }
}
