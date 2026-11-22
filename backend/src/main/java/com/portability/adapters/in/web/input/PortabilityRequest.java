package com.portability.adapters.in.web.input;

import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.StartPortabilityCommand;
import com.portability.domain.model.Validation;

import java.util.List;

/** Edge DTO; field invariants are owned by the domain. */
public record PortabilityRequest(
        String beneficiaryId,
        List<String> installmentIds,
        String creditorAssignee,
        ReceivingAccountRequest receivingAccount
) {
    public void validate() {
        Validation.requireNotBlank(beneficiaryId, "beneficiaryId");
        Validation.requireNotBlank(creditorAssignee, "creditorAssignee");
        Validation.requireSelection(installmentIds);
        account();
    }

    public StartPortabilityCommand toCommand() {
        return new StartPortabilityCommand(
                new BeneficiaryId(beneficiaryId), installmentIds, creditorAssignee, account());
    }

    private ReceivingAccount account() {
        if (receivingAccount == null) {
            throw new InvalidRequestException(
                    "receivingAccount", "receivingAccount must not be null");
        }
        return receivingAccount.toAccount();
    }
}
