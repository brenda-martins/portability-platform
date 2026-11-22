package com.portability.adapters.in.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed configuration of the simulated Central integration. */
@Validated
@ConfigurationProperties(prefix = "central")
public record CentralProperties(
        @NotBlank String baseUrl,
        @NotBlank String hmacSecret
) {
}
