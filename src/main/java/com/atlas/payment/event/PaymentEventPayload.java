package com.atlas.payment.event;

import com.atlas.payment.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Business payload shared by all Payment events (payment-events.yaml {@code PaymentEventPayload}).
 * {@code reason} is optional: present on PaymentFailed / PaymentTimedOut, absent on
 * PaymentRequested / PaymentSucceeded.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentEventPayload(
        @NotNull
        UUID paymentId,

        @NotNull
        UUID bookingId,

        @Valid
        @NotNull
        MoneyEvent amount,

        @NotNull
        PaymentStatus status,
        String reason
) {}
