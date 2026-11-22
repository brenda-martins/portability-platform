package com.portability.adapters.out.timing;

import com.portability.domain.ports.out.BackoffPort;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Blocks the current thread for the duration chosen by the application's backoff policy. */
@Component
public class ThreadBackoffAdapter implements BackoffPort {

    @Override
    public void await(Duration duration) throws InterruptedException {
        Thread.sleep(duration);
    }
}
