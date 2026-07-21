package com.atlas.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response body returned by the Fake Payment Provider (assumed contract; see
 * implementation_plan.md §Provider Contract Assumption).
 *
 * @param transactionId provider transaction id (present on APPROVED).
 * @param status        provider business status, e.g. {@code APPROVED} / {@code DECLINED}.
 * @param reason        optional human-readable cause (present on DECLINED).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderChargeResponse(String transactionId, String status, String reason) {}
