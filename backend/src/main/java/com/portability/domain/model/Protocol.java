package com.portability.domain.model;

public record Protocol(String value) {

    public Protocol {
        Validation.requireNotBlank(value, "protocol");
    }
}
