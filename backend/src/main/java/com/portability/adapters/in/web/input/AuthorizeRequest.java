package com.portability.adapters.in.web.input;

import jakarta.validation.constraints.NotBlank;

public record AuthorizeRequest(
        @NotBlank String beneficiaryId,
        @NotBlank String agreementId
) {
}
