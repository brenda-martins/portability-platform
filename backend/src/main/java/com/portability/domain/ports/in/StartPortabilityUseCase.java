package com.portability.domain.ports.in;

import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;

import java.util.List;

public interface StartPortabilityUseCase {

    PortabilityOperation startPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount
    );
}
