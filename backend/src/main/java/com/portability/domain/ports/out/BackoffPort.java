package com.portability.domain.ports.out;

import java.time.Duration;

/** Applies the wait between attempts to consult the Central. */
public interface BackoffPort {

    /** Waits for the given duration; an interruption is propagated to the caller. */
    void await(Duration duration) throws InterruptedException;
}
