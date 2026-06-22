package com.atlas.payment.entity;

/**
 * Lifecycle states of a {@link Payment} (services/payment/state_machine.md).
 * Payment state is independent from Booking state and owned solely by Payment Service.
 */
public enum PaymentStatus {

    /** Payment persisted; provider not yet called. Entered on InventoryReserved. Initial state. */
    CREATED,

    /** Request sent to the provider; awaiting outcome. Transient retries happen here. */
    PROCESSING,

    /** Provider approved the payment. Terminal. */
    SUCCEEDED,

    /** Provider rejected the payment, or a transient error persisted past max retries. Terminal. */
    FAILED,

    /** Provider did not respond before timeout, persisting past max retries. Terminal. */
    TIMED_OUT
}
