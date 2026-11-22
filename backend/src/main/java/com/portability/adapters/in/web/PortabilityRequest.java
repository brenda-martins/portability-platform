package com.portability.adapters.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PortabilityRequest(
        @NotBlank String beneficiaryId,
        @NotEmpty List<@NotBlank String> installmentIds,
        @NotBlank String creditorAssignee,
        @Valid @NotNull ReceivingAccountRequest receivingAccount
) {
}
