package com.atlas.payment.client;

import com.atlas.payment.entity.AttemptOutcome;

import java.util.List;

/**
 * The full result of charging the provider: every attempt made under the retry policy
 * (services/payment/service.md §Provider Call). The final attempt's outcome determines the terminal
 * Payment state: SUCCESS → SUCCEEDED, DECLINED / persistent TRANSIENT_ERROR → FAILED, persistent
 * TIMEOUT → TIMED_OUT.
 */
public record ProviderCallResult(List<ProviderAttemptRecord> attempts) {

    public ProviderCallResult {
        if (attempts == null || attempts.isEmpty()) {
            throw new IllegalArgumentException("A provider call must have at least one attempt");
        }
    }

    public ProviderAttemptRecord finalAttempt() {
        return attempts.get(attempts.size() - 1);
    }

    public AttemptOutcome finalOutcome() {
        return finalAttempt().outcome();
    }
}
