package com.portability.adapters.in.web;

import com.portability.adapters.in.web.input.PortabilityRequest;
import com.portability.adapters.in.web.input.ReceivingAccountRequest;
import com.portability.domain.model.PortabilityOperation;
import com.portability.domain.model.PortabilityOperationResult;
import com.portability.domain.model.ReceivingAccount;
import com.portability.domain.ports.in.GetOperationStatusUseCase;
import com.portability.domain.ports.in.StartPortabilityUseCase;
import com.portability.domain.ports.in.UpdateReceivingAccountUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST starts a portability operation. */
@RestController
@RequestMapping("/api/portability")
public class PortabilityController {

    private final StartPortabilityUseCase startPortabilityUseCase;
    private final GetOperationStatusUseCase getOperationStatusUseCase;
    private final UpdateReceivingAccountUseCase updateReceivingAccountUseCase;

    public PortabilityController(final StartPortabilityUseCase startPortabilityUseCase,
            final GetOperationStatusUseCase getOperationStatusUseCase,
            final UpdateReceivingAccountUseCase updateReceivingAccountUseCase) {
        this.startPortabilityUseCase = startPortabilityUseCase;
        this.getOperationStatusUseCase = getOperationStatusUseCase;
        this.updateReceivingAccountUseCase = updateReceivingAccountUseCase;
    }

    @PostMapping
    public ResponseEntity<PortabilityOperation> start(@Valid @RequestBody PortabilityRequest request) {
        ReceivingAccount account = new ReceivingAccount(
                request.receivingAccount().bank(),
                request.receivingAccount().agency(),
                request.receivingAccount().account());
        PortabilityOperationResult result = startPortabilityUseCase.startPortability(
                request.beneficiaryId(), request.installmentIds(), request.creditorAssignee(), account);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.operation());
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<PortabilityOperation> getStatus(@PathVariable String operationId) {
        return ResponseEntity.of(getOperationStatusUseCase.getOperationStatus(operationId));
    }

    @PutMapping("/{operationId}/receiving-account")
    public ResponseEntity<PortabilityOperation> updateReceivingAccount(
            @PathVariable String operationId,
            @Valid @RequestBody ReceivingAccountRequest request
    ) {
        ReceivingAccount account = new ReceivingAccount(
                request.bank(),
                request.agency(),
                request.account()
            );
        return ResponseEntity.of(
            updateReceivingAccountUseCase.updateReceivingAccount(operationId, account)
        );
    }
}
