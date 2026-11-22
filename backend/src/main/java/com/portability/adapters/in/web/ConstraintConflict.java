package com.portability.adapters.in.web;

import org.hibernate.exception.ConstraintViolationException;

final class ConstraintConflict {

    private ConstraintConflict() {
    }

    static boolean isViolationOf(Throwable exception, String constraintName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase().contains(constraintName.toLowerCase())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
