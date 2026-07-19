package com.atlas.payment.service;

import java.util.UUID;

/** Entry point for the Payment side of the booking choreography saga (services/payment/service.md). */
public interface PaymentService {

    /**
     * Processes a consumed {@code InventoryReserved} event: creates the Payment, charges the
     * provider under the retry policy, and resolves the Payment to a terminal state — each Payment
     * event committed via the outbox. Idempotent on the envelope {@code eventId} (EVT-005, EVT-010).
     */
    void onInventoryReserved(UUID eventId, InventoryReservedCommand command);

    /**
     * Resumes a payment stuck in {@code PROCESSING} (a crash between TX1 and TX2, ADR-0021):
     * re-drives the provider charge with the same {@code Idempotency-Key} ({@code paymentId} — the
     * provider replays the original outcome, never double-charging) and resolves to a terminal
     * state. No-ops if the payment vanished or is no longer PROCESSING.
     */
    void recoverStalePayment(UUID paymentId);
}
