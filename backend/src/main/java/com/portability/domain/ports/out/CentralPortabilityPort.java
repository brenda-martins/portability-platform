package com.portability.domain.ports.out;

import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.ReceivingAccount;

import java.util.List;

/** Outbound port for the external Central de Convênios integration. */
public interface CentralPortabilityPort {

    /** Registers the operation and returns the protocol supplied by the Central. */
    String registerPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount,
            String signature
    );

    /** Queries the asynchronous confirmation for a protocol. */
    CentralOperationStatus consultStatus(String protocol);
}
