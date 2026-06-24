package com.atlas.payment.shared.messaging;

/**
 * Event types consumed by Payment Service. Recorded on each {@code ConsumedEvent} row for
 * idempotency (EVT-005, EVT-010). Payment's single trigger is {@code InventoryReserved}.
 */
public enum ConsumerEventType {
    INVENTORY_RESERVED
}
