package com.portability.adapters.out.central;

import com.portability.adapters.in.config.CentralProperties;
import com.portability.domain.model.CentralOperationStatus;
import com.portability.domain.model.DiscountInstallment;
import com.portability.domain.model.InstallmentStatus;
import com.portability.domain.model.OperationStatus;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.out.CentralAuthorizationPort;
import com.portability.domain.ports.out.CentralPortabilityPort;
import com.portability.domain.ports.out.CentralUnavailableException;
import com.portability.domain.ports.out.DiscountAgendaPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

@Component
public class CentralClient implements DiscountAgendaPort, CentralPortabilityPort, CentralAuthorizationPort {

    private final RestClient restClient;
    private final CentralRequestCodec codec;
    private final ObjectMapper objectMapper;

    @Autowired
    public CentralClient(CentralProperties properties, CentralRequestCodec codec, ObjectMapper objectMapper) {
        this(properties.baseUrl(), codec, objectMapper);
    }

    public CentralClient(String centralBaseUrl, HmacSignatureService signatureService, ObjectMapper objectMapper) {
        this(centralBaseUrl, new CentralRequestCodec(signatureService, objectMapper), objectMapper);
    }

    private CentralClient(String centralBaseUrl, CentralRequestCodec codec, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder()
                .baseUrl(centralBaseUrl)
                .requestFactory(requestFactory)
                .build();
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    @Override
    public String registerAuthorization(String beneficiaryId, String agreementId) {
        CentralRequestCodec.SignedRequest request = codec.authorization(beneficiaryId, agreementId);
        try {
            ResponseEntity<CentralAuthorizationResponse> response = restClient.post()
                    .uri("/autorizacoes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", request.signature())
                    .body(request.body())
                    .retrieve()
                    .toEntity(CentralAuthorizationResponse.class);

            CentralAuthorizationResponse payload = response.getBody();
            if (response.getStatusCode() != HttpStatus.CREATED
                    || payload == null || isBlank(payload.autorizacaoId())) {
                throw new CentralUnavailableException("Unexpected response from Central");
            }
            return payload.autorizacaoId();
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    @Override
    public List<DiscountInstallment> fetchAgenda(String beneficiaryId) {
        try {
            ResponseEntity<CentralInstallment[]> response = restClient.get()
                    .uri("/beneficiarios/{beneficiaryId}/agenda", beneficiaryId)
                    .retrieve()
                    .toEntity(CentralInstallment[].class);
            CentralInstallment[] items = response.getBody();
            if (response.getStatusCode() != HttpStatus.OK || items == null) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            return Arrays.stream(items).map(CentralClient::toDomain).toList();
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    private static DiscountInstallment toDomain(CentralInstallment item) {
        if (item == null || isBlank(item.parcelaId()) || isBlank(item.convenioId())
                || item.valor() == null || isBlank(item.dataDesconto()) || isBlank(item.status())) {
            throw new CentralUnavailableException("Resposta inesperada da Central");
        }
        InstallmentStatus status = switch (item.status()) {
            case "ABERTA" -> InstallmentStatus.OPEN;
            case "PORTADA" -> InstallmentStatus.PORTED;
            case "CANCELADA" -> InstallmentStatus.CANCELLED;
            default -> throw new CentralUnavailableException("Resposta inesperada da Central");
        };
        try {
            return new DiscountInstallment(item.parcelaId(), item.convenioId(), item.valor(),
                    LocalDate.parse(item.dataDesconto()), status);
        } catch (DateTimeParseException exception) {
            throw new CentralUnavailableException("Resposta inesperada da Central", exception);
        }
    }

    @Override
    public String registerPortability(String beneficiaryId, List<String> installmentIds,
            String creditorAssignee, ReceivingAccount receivingAccount) {
        CentralRequestCodec.SignedRequest request = codec.portability(
                beneficiaryId, installmentIds, creditorAssignee, receivingAccount);
        try {
            ResponseEntity<CentralOperationResponse> response = restClient.post()
                    .uri("/operacoes-portabilidade")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", request.signature())
                    .body(request.body())
                    .retrieve()
                    .toEntity(CentralOperationResponse.class);

            CentralOperationResponse result = response.getBody();
            if (response.getStatusCode() != HttpStatus.ACCEPTED
                    || result == null || isBlank(result.protocolo())) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            return result.protocolo();
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    @Override
    public CentralOperationStatus consultStatus(String protocol) {
        try {
            ResponseEntity<CentralOperationStatusResponse> response = restClient.get()
                    .uri("/operacoes-portabilidade/{protocol}", protocol)
                    .retrieve()
                    .toEntity(CentralOperationStatusResponse.class);

            if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                return new CentralOperationStatus(OperationStatus.PENDING, null, retryAfter(response));
            }
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            CentralOperationStatusResponse payload = response.getBody();
            if (payload == null || isBlank(payload.status())) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            OperationStatus status = switch (payload.status()) {
                case "PENDENTE" -> OperationStatus.PENDING;
                case "CONFIRMADA" -> OperationStatus.CONFIRMED;
                case "REJEITADA" -> OperationStatus.REJECTED;
                default -> throw new CentralUnavailableException("Resposta inesperada da Central");
            };
            if (status == OperationStatus.REJECTED && isBlank(payload.motivoRejeicao())) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            Duration retryAfter = status == OperationStatus.PENDING ? retryAfter(response) : Duration.ZERO;
            return new CentralOperationStatus(status, payload.motivoRejeicao(), retryAfter);
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    @Override
    public ReceivingAccount updateReceivingAccount(String protocol, ReceivingAccount receivingAccount) {
        CentralRequestCodec.SignedRequest request = codec.accountUpdate(protocol, receivingAccount);
        try {
            ResponseEntity<CentralReceivingAccountResponse> response = restClient.put()
                    .uri("/operacoes-portabilidade/{protocol}/conta-recebimento", protocol)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Signature", request.signature())
                    .body(request.body())
                    .retrieve()
                    .toEntity(CentralReceivingAccountResponse.class);

            CentralReceivingAccountResponse result = response.getBody();
            if (response.getStatusCode() != HttpStatus.OK
                    || result == null || isBlank(result.banco())
                    || isBlank(result.agencia()) || isBlank(result.conta())) {
                throw new CentralUnavailableException("Resposta inesperada da Central");
            }
            return new ReceivingAccount(result.banco(), result.agencia(), result.conta());
        } catch (RestClientException | JacksonException exception) {
            throw new CentralUnavailableException("Central indisponível", exception);
        }
    }

    private static Duration retryAfter(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null || header.isBlank()) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException exception) {
            throw new CentralUnavailableException("Retry-After inválido da Central", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CentralAuthorizationResponse(String autorizacaoId) {
    }

    private record CentralOperationResponse(String protocolo) {
    }

    private record CentralOperationStatusResponse(String status, String motivoRejeicao) {
    }

    private record CentralReceivingAccountResponse(String banco, String agencia, String conta) {
    }

    private record CentralInstallment(String parcelaId, String convenioId, BigDecimal valor,
                                      String dataDesconto, String status) {
    }
}
