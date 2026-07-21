package com.atlas.payment.shared.messaging;

/**
 * Kafka topic name constants (topics.md, naming: domain.entity.event).
 * Topics prefixed payment.* are owned by Payment Service. The inventory.* topic is owned by
 * Inventory Service; the constant is defined here for consumer reference only. Topic names are
 * immutable — never rename or reuse a topic.
 */
public final class EventTopics {

    // ── Payment Service produces (payment-events.yaml) ──
    public static final String PAYMENT_REQUESTED = "payment.requested";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_TIMED_OUT = "payment.timed_out";

    // ── Payment Service consumes (owned by Inventory Service) ──
    // Single trigger: carries bookingId + amount (service.md §Trigger & Amount Source).
    public static final String INVENTORY_RESERVED = "inventory.reserved";

    private EventTopics() {}
}
