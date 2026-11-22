package com.portability.adapters.in.web.input;

import jakarta.validation.constraints.NotBlank;

public record ReceivingAccountRequest(
        @NotBlank String bank,
        @NotBlank String agency,
        @NotBlank String account
) {
}
