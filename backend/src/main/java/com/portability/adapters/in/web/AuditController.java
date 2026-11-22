package com.portability.adapters.in.web;

import com.portability.adapters.in.web.output.AuditEventResponse;
import com.portability.domain.model.OperationId;
import com.portability.domain.ports.in.GetOperationAuditUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portability/{operationId}/audit")
public class AuditController {

    private final GetOperationAuditUseCase getOperationAuditUseCase;

    public AuditController(final GetOperationAuditUseCase getOperationAuditUseCase) {
        this.getOperationAuditUseCase = getOperationAuditUseCase;
    }

    @GetMapping
    public ResponseEntity<List<AuditEventResponse>> getAuditTrail(@PathVariable String operationId) {
        return ResponseEntity.of(getOperationAuditUseCase.getAuditTrail(new OperationId(operationId))
                .map(events -> events.stream().map(AuditEventResponse::from).toList()));
    }
}
