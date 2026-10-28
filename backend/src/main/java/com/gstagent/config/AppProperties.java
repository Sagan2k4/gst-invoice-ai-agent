package com.gstagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String tokenStore,
        Ai ai,
        Storage storage,
        Validation validation,
        Cors cors
) {
    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Ai(String provider, int maxCorrectionAttempts) {}

    public record Storage(String dir) {}

    public record Validation(BigDecimal amountTolerance) {}

    public record Cors(List<String> allowedOrigins) {}
}
