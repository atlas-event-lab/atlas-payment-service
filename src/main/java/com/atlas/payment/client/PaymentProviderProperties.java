package com.atlas.payment.client;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Fake Payment Provider call (services/payment/service.md
 * §Provider Call: Timeout &amp; Retry). No hardcoded values (coding-standards §Configuration).
 *
 * @param baseUrl    provider base URL (WireMock in local/dev).
 * @param timeout    per-call timeout; a call exceeding it is a transient TIMEOUT (default 5s).
 * @param maxAttempts total attempts = 1 initial + retries (default 3).
 * @param backoff    wait BEFORE each attempt, indexed by attempt (0s, 2s, 5s); entries beyond the
 *                   list size fall back to no wait.
 * @param scenario   optional WireMock scenario forwarded as {@code X-Payment-Scenario}; when unset
 *                   the provider defaults to SUCCESS. A demo-only lever (e.g. set DECLINED to make
 *                   every charge decline); production leaves it unset.
 */
@ConfigurationProperties(prefix = "atlas.payment.provider")
public record PaymentProviderProperties(
        String baseUrl, Duration timeout, int maxAttempts, List<Duration> backoff, String scenario) {
    /** Wait to apply before the given 1-based attempt; no wait once past the configured list. */
    public Duration backoffBefore(int attemptNumber) {
        int index = attemptNumber - 1;
        if (backoff == null || index < 0 || index >= backoff.size()) {
            return Duration.ZERO;
        }
        return backoff.get(index);
    }
}
