package com.portability.domain.ports.out;

import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.StartPortabilityCommand;

/** Outbound port for the external Central de Convênios integration. */
public interface CentralPortabilityPort {

    /** Registers the signed operation and returns the protocol supplied by the Central. */
    Protocol registerPortability(StartPortabilityCommand command);

    /** Queries the asynchronous confirmation for a protocol. */
    CentralOperationStatus consultStatus(Protocol protocol);

    /** Updates the receiving account of a confirmed operation, returning the account accepted by the Central. */
    ReceivingAccount updateReceivingAccount(Protocol protocol, ReceivingAccount receivingAccount);
}
