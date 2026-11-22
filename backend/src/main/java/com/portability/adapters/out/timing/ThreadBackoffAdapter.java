package com.portability.adapters.out.timing;

import com.portability.domain.ports.out.BackoffPort;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Blocks the current thread for the duration chosen by the application's backoff policy. */
@Component
public class ThreadBackoffAdapter implements BackoffPort {

    @Override
    public void await(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Central status", exception);
        }
    }
}
