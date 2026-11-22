package com.portability.domain.ports.out;

public class CentralUnavailableException extends RuntimeException {

    public CentralUnavailableException(String message) {
        super(message);
    }

    public CentralUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
