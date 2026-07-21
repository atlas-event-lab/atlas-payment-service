package com.atlas.payment.service;

import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.exception.InvalidPaymentStateTransitionException;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the Payment state machine (services/payment/state_machine.md).
 * Only {@code CREATED → PROCESSING} and {@code PROCESSING → {SUCCEEDED, FAILED, TIMED_OUT}} are
 * allowed. Terminal states (SUCCEEDED, FAILED, TIMED_OUT) have no outgoing transitions and in
 * particular SHALL NOT return to PROCESSING.
 */
public final class PaymentStateTransitionGuard {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.CREATED, Set.of(PaymentStatus.PROCESSING),
            PaymentStatus.PROCESSING, Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.TIMED_OUT));

    private PaymentStateTransitionGuard() {}

    /**
     * Asserts the transition from {@code from} to {@code to} is allowed.
     *
     * @throws InvalidPaymentStateTransitionException if the transition is forbidden.
     */
    public static void assertAllowed(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> reachable = ALLOWED.getOrDefault(from, Set.of());
        if (!reachable.contains(to)) {
            throw new InvalidPaymentStateTransitionException(from, to);
        }
    }
}
