package com.portability.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvalidRequestException extends DomainException {

    private final Map<String, String> fields;

    public InvalidRequestException(String message) {
        this(message, Map.of());
    }

    public InvalidRequestException(String field, String message) {
        this(message, Map.of(field, message));
    }

    public InvalidRequestException(String message, Map<String, String> fields) {
        super(message);
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> fields() {
        return fields;
    }
}
