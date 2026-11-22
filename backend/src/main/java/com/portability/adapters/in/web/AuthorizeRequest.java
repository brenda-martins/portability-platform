package com.portability.adapters.in.web;

import jakarta.validation.constraints.NotBlank;

public record AuthorizeRequest(
        @NotBlank String beneficiaryId,
        @NotBlank String agreementId
) {
}
