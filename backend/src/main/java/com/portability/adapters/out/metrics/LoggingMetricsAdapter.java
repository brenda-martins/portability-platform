package com.portability.adapters.out.metrics;

import com.portability.domain.ports.out.MetricsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-process counters for the seams of the application, logged for local observability. */
@Component
public class LoggingMetricsAdapter implements MetricsPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingMetricsAdapter.class);

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void increment(String metric) {
        long value = counters.computeIfAbsent(metric, ignored -> new AtomicLong()).incrementAndGet();
        log.debug("metric={} count={}", metric, value);
    }
}
