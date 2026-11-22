package com.portability.adapters.out.central;

import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.DiscountAgendaPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbound HTTP adapter for the Central de Convênios.
 * TODO: RestClient calls, HMAC headers, timeout, 202 + Retry-After polling with backoff.
 */
@Component
public class CentralClient implements DiscountAgendaPort, CentralPortabilityPort {

    @Value("${central.base-url}")
    private String centralBaseUrl;

    @Override
    public List<DiscountInstallment> fetchAgenda(String beneficiaryId) {
        // TODO: GET {centralBaseUrl}/beneficiarios/{beneficiaryId}/agenda
        throw new UnsupportedOperationException("TODO: fetch agenda from Central");
    }

    @Override
    public String registerPortability(
            String beneficiaryId,
            List<String> installmentIds,
            String creditorAssignee,
            ReceivingAccount receivingAccount,
            String signature
    ) {
        // TODO: POST {centralBaseUrl}/operacoes-portabilidade -> protocol (202 Accepted)
        throw new UnsupportedOperationException("TODO: register portability at Central");
    }

    @Override
    public CentralOperationStatus consultStatus(String protocol) {
        // TODO: GET {centralBaseUrl}/operacoes-portabilidade/{protocol} -> status / Retry-After
        throw new UnsupportedOperationException("TODO: consult Central status");
    }
}
