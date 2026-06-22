package com.atlas.payment.dto;

import com.atlas.payment.entity.PaymentStatus;

import java.util.UUID;

/**
 * Payment representation returned by {@code GET /payments/{paymentId}} (payment.yaml
 * {@code PaymentResponse}). The entity {@link PaymentStatus} serializes 1:1 to the contract enum
 * (CREATED / PROCESSING / SUCCEEDED / FAILED / TIMED_OUT).
 */
public record PaymentResponse(
        UUID paymentId,
        UUID bookingId,
        PaymentStatus status,
        MoneyResponse amount
) {}
