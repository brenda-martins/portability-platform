package com.portability.domain.ports.in;

import com.portability.domain.model.OperationId;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.ReceivingAccount;

import java.util.Optional;

public interface UpdateReceivingAccountUseCase {

    Optional<PortabilityOperation> updateReceivingAccount(
            OperationId operationId, ReceivingAccount receivingAccount);
}
