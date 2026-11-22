package com.portability.domain.ports.in;

import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;

import java.util.List;

public interface StartPortabilityUseCase {

    PortabilityOperationResult startPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount
    );
}
