package com.portability.adapters.in.web;

import com.portability.domain.model.BeneficiaryNotAuthorizedException;
import com.portability.domain.model.ConcurrentOperationException;
import com.portability.domain.model.DomainException;
import com.portability.domain.model.InvalidRequestException;
import com.portability.domain.model.OperationNotConfirmedException;
import com.portability.domain.ports.out.CentralUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates application failures into the API error contract. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidRequestException exception) {
        Map<String, Object> body = errorBody(exception.getMessage());
        if (!exception.fields().isEmpty()) {
            body.put("fields", exception.fields());
        }
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BeneficiaryNotAuthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleNotAuthorized(BeneficiaryNotAuthorizedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(OperationNotConfirmedException.class)
    public ResponseEntity<Map<String, Object>> handleNotConfirmed(OperationNotConfirmedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(ConcurrentOperationException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentOperation(ConcurrentOperationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(CentralUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleCentralFailure(CentralUnavailableException exception) {
        log.warn("Central integration failure: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, Object>> handleDomainFailure(DomainException exception) {
        return ResponseEntity.badRequest().body(errorBody(exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(DataIntegrityViolationException exception) {
        String message = ConstraintConflict.isViolationOf(exception, "idempotency")
                ? "Operation already registered for this request"
                : "Uniqueness conflict in the submitted data";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(message));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentUpdate(
            OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("Operation changed by another request; please try again"));
    }

    private static Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        return body;
    }
}
