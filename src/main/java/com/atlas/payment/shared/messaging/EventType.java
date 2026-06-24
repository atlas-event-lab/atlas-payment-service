package com.atlas.payment.shared.messaging;

/**
 * Produced event types (payment-events.yaml message names). Stored on each outbox row and used by
 * {@code OutboxRelay} to resolve the destination topic. Names are past-tense, completed facts
 * (events.md §Naming Rules).
 */
public enum EventType {
    PAYMENT_REQUESTED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_TIMED_OUT
}
