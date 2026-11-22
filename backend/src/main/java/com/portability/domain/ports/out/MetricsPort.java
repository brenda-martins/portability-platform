package com.portability.domain.ports.out;

public interface MetricsPort {

    void increment(String metric);
}
