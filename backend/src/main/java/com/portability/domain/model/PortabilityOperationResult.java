package com.portability.domain.model;

/** Result of a portability request: the operation and whether it was newly created. */
public record PortabilityOperationResult(PortabilityOperation operation, boolean created) {
}
