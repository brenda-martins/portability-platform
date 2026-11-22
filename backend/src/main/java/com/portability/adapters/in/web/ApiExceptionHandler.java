package com.portability.adapters.in.web;

import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.OperationNotConfirmedException;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Translates application failures into the API error contract. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "Solicitação inválida: verifique os campos obrigatórios"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(BeneficiaryNotAuthorizedException.class)
    public ResponseEntity<Map<String, String>> handleNotAuthorized(BeneficiaryNotAuthorizedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(OperationNotConfirmedException.class)
    public ResponseEntity<Map<String, String>> handleNotConfirmed(OperationNotConfirmedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(CentralUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleCentralFailure(CentralUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleConcurrentUpdate(
            OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Operação alterada por outra requisição; tente novamente"));
    }
}
