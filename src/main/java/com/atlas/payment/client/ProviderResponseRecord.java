package com.atlas.payment.client;

import java.time.Instant;

/**
 * The provider's HTTP response captured for one attempt. Absent (null on the owning
 * {@link ProviderAttemptRecord}) when the call timed out with no response.
 */
public record ProviderResponseRecord(
        int httpStatus,
        String providerStatus,
        String transactionId,
        String reason,
        Instant receivedAt
) {}
