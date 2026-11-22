package com.portability.adapters.in.web;

import com.portability.domain.model.Authorization;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/authorizations")
public class AuthorizationController {

    private final RegisterAuthorizationUseCase registerAuthorizationUseCase;

    public AuthorizationController(RegisterAuthorizationUseCase registerAuthorizationUseCase) {
        this.registerAuthorizationUseCase = registerAuthorizationUseCase;
    }

    @PostMapping
    public ResponseEntity<Authorization> register(@Valid @RequestBody AuthorizeRequest request) {
        AuthorizationResult result = registerAuthorizationUseCase.registerAuthorization(
                request.beneficiaryId(), request.agreementId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.authorization());
    }
}
