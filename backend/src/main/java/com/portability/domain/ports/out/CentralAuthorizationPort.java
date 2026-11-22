package com.portability.domain.ports.out;

public interface CentralAuthorizationPort {

    String registerAuthorization(String beneficiaryId, String agreementId);
}
