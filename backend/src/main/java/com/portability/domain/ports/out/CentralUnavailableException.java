package com.portability.domain.ports.out;

import com.portability.domain.model.DomainException;

public class CentralUnavailableException extends DomainException {

    public CentralUnavailableException(String message) {
        super(message);
    }

    public CentralUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
