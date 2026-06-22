package com.atlas.payment.event;

/**
 * Produced event {@code eventType} names (payment-events.yaml message names). Stored on each outbox
 * row and used by {@code OutboxRelay} to resolve the destination topic. Names are past-tense,
 * completed facts (events.md §Naming Rules).
 */
public final class PaymentEventTypes {

    public static final String PAYMENT_REQUESTED  = "PaymentRequested";
    public static final String PAYMENT_SUCCEEDED  = "PaymentSucceeded";
    public static final String PAYMENT_FAILED     = "PaymentFailed";
    public static final String PAYMENT_TIMED_OUT  = "PaymentTimedOut";

    private PaymentEventTypes() {}
}
