package com.portability.domain.ports.out;

import java.time.Duration;

/** Applies the wait between attempts to consult the Central about an operation. */
public interface BackoffPort {

    void await(Duration duration);
}
