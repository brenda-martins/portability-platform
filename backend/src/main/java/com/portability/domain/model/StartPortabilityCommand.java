package com.portability.domain.model;

import java.util.List;

public record StartPortabilityCommand(
        BeneficiaryId beneficiaryId,
        List<String> installmentIds,
        String creditorAssignee,
        ReceivingAccount receivingAccount
) {
    public StartPortabilityCommand {
        Validation.requireNonNull(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(creditorAssignee, "creditorAssignee");
        installmentIds = Validation.requireSelection(installmentIds);
        Validation.requireAccount(receivingAccount);
    }
}
