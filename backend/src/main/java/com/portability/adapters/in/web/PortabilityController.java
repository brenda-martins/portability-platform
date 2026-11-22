package com.portability.adapters.in.web;

import com.portability.adapters.in.web.input.PortabilityRequest;
import com.portability.adapters.in.web.input.ReceivingAccountRequest;
import com.portability.adapters.in.web.output.OperationResponse;
import com.portability.domain.model.OperationId;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.in.UpdateReceivingAccountUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portability")
public class PortabilityController {

    private final StartPortabilityUseCase startPortabilityUseCase;
    private final GetOperationStatusUseCase getOperationStatusUseCase;
    private final UpdateReceivingAccountUseCase updateReceivingAccountUseCase;

    public PortabilityController(
        final StartPortabilityUseCase startPortabilityUseCase,
        final GetOperationStatusUseCase getOperationStatusUseCase,
        final UpdateReceivingAccountUseCase updateReceivingAccountUseCase
    ) {
        this.startPortabilityUseCase = startPortabilityUseCase;
        this.getOperationStatusUseCase = getOperationStatusUseCase;
        this.updateReceivingAccountUseCase = updateReceivingAccountUseCase;
    }

    @PostMapping
    public ResponseEntity<OperationResponse> start(@RequestBody PortabilityRequest request) {
        ReceivingAccount account = request.validate();
        PortabilityOperationResult result = startPortabilityUseCase.startPortability(request.toCommand(account));
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(OperationResponse.from(result.operation()));
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<OperationResponse> getStatus(@PathVariable String operationId) {
        return ResponseEntity.of(getOperationStatusUseCase.getOperationStatus(new OperationId(operationId))
                .map(OperationResponse::from));
    }

    @PutMapping("/{operationId}/receiving-account")
    public ResponseEntity<OperationResponse> updateReceivingAccount(
            @PathVariable String operationId,
            @RequestBody ReceivingAccountRequest request
    ) {
        request.validate();
        return ResponseEntity.of(updateReceivingAccountUseCase
                .updateReceivingAccount(new OperationId(operationId), request.toAccount())
                .map(OperationResponse::from));
    }
}
