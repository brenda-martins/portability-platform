package com.portability.domain.model;

import java.time.Duration;

/** Status returned by the Central de Convênios for a given protocol. */
public record CentralOperationStatus(OperationStatus status, String rejectionReason, Duration retryAfter) {
}
