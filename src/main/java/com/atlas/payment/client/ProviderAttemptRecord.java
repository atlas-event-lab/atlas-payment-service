package com.atlas.payment.client;

import com.atlas.payment.entity.AttemptOutcome;
import java.time.Instant;

/**
 * Outcome and timing of a single provider call attempt, plus the provider response if one was
 * received. Maps 1:1 to a persisted {@code PaymentAttempt} (+ optional {@code PaymentProviderResponse}).
 */
public record ProviderAttemptRecord(
        int attemptNumber,
        AttemptOutcome outcome,
        Instant startedAt,
        Instant completedAt,
        String errorDetail,
        ProviderResponseRecord response) {}
