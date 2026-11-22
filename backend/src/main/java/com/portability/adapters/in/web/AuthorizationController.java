package com.portability.adapters.in.web;

import com.portability.adapters.in.web.input.AuthorizeRequest;
import com.portability.adapters.in.web.output.AuthorizationResponse;
import com.portability.domain.model.AuthorizationResult;
import com.portability.domain.ports.in.RegisterAuthorizationUseCase;
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

    public AuthorizationController(final RegisterAuthorizationUseCase registerAuthorizationUseCase) {
        this.registerAuthorizationUseCase = registerAuthorizationUseCase;
    }

    @PostMapping
    public ResponseEntity<AuthorizationResponse> register(@RequestBody AuthorizeRequest request) {
        request.validate();
        AuthorizationResult result = registerAuthorizationUseCase.registerAuthorization(
                request.beneficiaryId(), 
                request.agreementId()
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(AuthorizationResponse.from(result.authorization()));
    }
}
