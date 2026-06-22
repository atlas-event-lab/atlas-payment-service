package com.atlas.payment.exception;

import com.atlas.payment.entity.PaymentStatus;

/** Thrown when a Payment state transition violates services/payment/state_machine.md. */
public class InvalidPaymentStateTransitionException extends RuntimeException {

    public InvalidPaymentStateTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Invalid payment state transition: " + from + " → " + to);
    }
}
