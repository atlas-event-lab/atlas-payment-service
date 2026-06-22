package com.atlas.payment.client.dto;

import java.math.BigDecimal;

/** Money in the provider request body (assumed provider contract; see implementation_plan.md). */
public record MoneyDto(BigDecimal amount, String currency) {}
