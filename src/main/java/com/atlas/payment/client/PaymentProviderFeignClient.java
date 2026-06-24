package com.atlas.payment.client;

import com.atlas.payment.client.dto.ProviderChargeRequest;
import com.atlas.payment.client.dto.ProviderChargeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign binding for the Fake Payment Provider (coding-standards.md §Rest Client; assumed contract:
 * {@code POST {base-url}/payments}). Timeouts are supplied by the {@code Request.Options} bean in
 * {@link com.atlas.payment.config.PaymentProviderConfig}; the timeout/retry/backoff policy lives in
 * {@link PaymentProviderClient}, so this client performs no retry of its own.
 * <p>
 * The 2xx body is returned as a {@link ResponseEntity}; non-2xx responses surface as a
 * {@code FeignException} (HTTP status) and I/O failures (incl. socket timeouts) as a
 * {@code RetryableException}, both translated to attempt outcomes by {@link PaymentProviderClient}.
 */
@FeignClient(name = "payment-provider", url = "${atlas.payment.provider.base-url}")
public interface PaymentProviderFeignClient {

    /**
     * Charges the provider. {@code scenario} is omitted from the request when {@code null}
     * (production leaves it unset; service.md §Fake Payment Provider).
     */
    @PostMapping(value = "/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProviderChargeResponse> charge(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Payment-Scenario", required = false) String scenario,
            @RequestBody ProviderChargeRequest request);
}
