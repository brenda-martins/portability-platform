package com.portability.adapters.out.central;

import com.portability.domain.model.AgreementId;
import com.portability.domain.model.BeneficiaryId;
import com.portability.domain.model.Protocol;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.model.StartPortabilityCommand;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the canonical pt-BR bodies of the Central and signs them in one place. */
@Component
public class CentralRequestCodec {

    private final HmacSignatureService signer;
    private final ObjectMapper objectMapper;

    public CentralRequestCodec(HmacSignatureService signer, ObjectMapper objectMapper) {
        this.signer = signer;
        this.objectMapper = objectMapper;
    }

    public record SignedRequest(String body, String signature) {
    }

    public SignedRequest authorization(BeneficiaryId beneficiaryId, AgreementId agreementId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("beneficiarioId", beneficiaryId.value());
        payload.put("convenioId", agreementId.value());
        return sign(payload);
    }

    public SignedRequest portability(StartPortabilityCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("beneficiarioId", command.beneficiaryId().value());
        payload.put("parcelas", command.installmentIds());
        payload.put("credorCessionario", command.creditorAssignee());
        ReceivingAccount account = command.receivingAccount();
        Map<String, String> accountPayload = new LinkedHashMap<>();
        accountPayload.put("banco", account.bank());
        accountPayload.put("agencia", account.agency());
        accountPayload.put("conta", account.account());
        payload.put("contaRecebimento", accountPayload);
        return sign(payload);
    }

    public SignedRequest accountUpdate(Protocol protocol, ReceivingAccount account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("protocolo", protocol.value());
        payload.put("banco", account.bank());
        payload.put("agencia", account.agency());
        payload.put("conta", account.account());
        return sign(payload);
    }

    /** Canonical form used both when signing and when verifying a Central request. */
    public Optional<String> canonicalBody(String body) {
        try {
            JsonNode payload = objectMapper.readTree(body);
            if (payload == null || !payload.isObject()) {
                return Optional.empty();
            }
            ObjectNode canonical = ((ObjectNode) payload).deepCopy();
            canonical.remove("assinatura");
            return Optional.of(objectMapper.writeValueAsString(canonical));
        } catch (JacksonException exception) {
            return Optional.empty();
        }
    }

    public boolean isValidSignature(String body, String signature) {
        return signature != null
                && canonicalBody(body).map(canonical -> signer.verify(canonical, signature)).orElse(false);
    }

    private SignedRequest sign(Map<String, Object> payload) {
        try {
            String canonical = objectMapper.writeValueAsString(payload);
            String signature = signer.sign(canonical);
            Map<String, Object> withSignature = new LinkedHashMap<>(payload);
            withSignature.put("assinatura", signature);
            return new SignedRequest(objectMapper.writeValueAsString(withSignature), signature);
        } catch (JacksonException exception) {
            throw new CentralUnavailableException("Could not build Central request", exception);
        }
    }
}
