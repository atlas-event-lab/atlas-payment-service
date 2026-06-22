package com.atlas.payment.exception;

import java.util.UUID;

/** Thrown when a {@code GET /payments/{paymentId}} targets a payment that does not exist (404). */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
    }
}
