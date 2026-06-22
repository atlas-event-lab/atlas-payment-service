package com.atlas.payment.dto;

import java.math.BigDecimal;

/** Monetary amount in the API representation (payment.yaml → common/money.yaml Money). */
public record MoneyResponse(BigDecimal amount, String currency) {}
