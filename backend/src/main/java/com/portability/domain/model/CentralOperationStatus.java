package com.portability.domain.model;

/** Status returned by the Central de Convênios for a given protocol. */
public record CentralOperationStatus(OperationStatus status, String rejectionReason) {
}
