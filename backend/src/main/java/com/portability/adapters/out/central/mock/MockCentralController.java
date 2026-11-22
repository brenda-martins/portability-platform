package com.portability.adapters.out.central.mock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Local simulation of the Central de Convênios, following the contract suggested in the PDF.
 * TODO: 201 + id, agenda data, 202 + protocol, delayed confirmation (204 + Retry-After),
 * occasional rejections/timeouts and HMAC verification.
 */
@RestController
@RequestMapping("/central")
public class MockCentralController {

    @PostMapping("/autorizacoes")
    public ResponseEntity<Void> registerAuthorization(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/beneficiarios/{beneficiaryId}/agenda")
    public ResponseEntity<Void> getAgenda(@PathVariable String beneficiaryId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @PostMapping("/operacoes-portabilidade")
    public ResponseEntity<Void> registerPortability(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/operacoes-portabilidade/{protocol}")
    public ResponseEntity<Void> getStatus(@PathVariable String protocol) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
