package com.atlas.payment.event;

import java.math.BigDecimal;

/** Money as carried inside event payloads (payment-events.yaml → common/money.yaml Money). */
public record MoneyEvent(BigDecimal amount, String currency) {}
