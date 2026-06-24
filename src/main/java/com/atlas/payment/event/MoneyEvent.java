package com.atlas.payment.event;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Money as carried inside event payloads (payment-events.yaml → common/money.yaml Money). */
public record MoneyEvent(
        @NotNull
        BigDecimal amount,
        String currency
) {}
