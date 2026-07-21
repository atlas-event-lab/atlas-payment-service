package com.atlas.payment.client.dto;

import java.util.UUID;

/**
 * Request body sent to the Fake Payment Provider.
 * <p>
 * <b>Assumed provider contract</b> (the provider's HTTP schema is not specified in the Atlas docs;
 * see docs/features/payment/implementation_plan.md §Provider Contract Assumption):
 * {@code POST {base-url}/payments} with header {@code Idempotency-Key: <paymentId>}.
 */
public record ProviderChargeRequest(UUID paymentId, UUID bookingId, MoneyDto amount) {}
