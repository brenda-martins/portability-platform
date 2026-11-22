package com.portability.adapters.in.web.input;

import com.portability.domain.model.Validation;

/** Edge DTO; field invariants are owned by the domain. */
public record AuthorizeRequest(String beneficiaryId, String agreementId) {

    public void validate() {
        Validation.requireNotBlank(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(agreementId, "agreementId");
    }
}
