package com.atlas.payment.client;

import com.atlas.payment.client.dto.MoneyDto;
import com.atlas.payment.client.dto.ProviderChargeRequest;
import com.atlas.payment.client.dto.ProviderChargeResponse;
import com.atlas.payment.entity.AttemptOutcome;
import com.atlas.payment.entity.Money;
import com.atlas.payment.entity.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls the Fake Payment Provider, applying the timeout/retry policy from
 * services/payment/service.md §Provider Call: per-call timeout, up to {@code maxAttempts}
 * (1 initial + retries), backoff {@code 0s/2s/5s}. Only transient failures (timeout, HTTP 503) are
 * retried; business failures (DECLINED → 402/422) are never retried (EVT-008). Every request carries
 * an {@code Idempotency-Key} derived from the {@code paymentId} so a duplicate never double-charges.
 * <p>
 * This client performs no persistence and no state change — it returns a {@link ProviderCallResult}
 * describing every attempt; the service maps it to the terminal Payment state and history.
 */
@Slf4j
@Component
public class PaymentProviderClient {

    private static final String PAYMENTS_PATH = "/payments";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String SCENARIO_HEADER = "X-Payment-Scenario";

    private final RestClient restClient;
    private final PaymentProviderProperties properties;
    private final Sleeper sleeper;
    private final Clock clock;

    public PaymentProviderClient(RestClient paymentProviderRestClient,
                                 PaymentProviderProperties properties,
                                 Sleeper sleeper,
                                 Clock clock) {
        this.restClient = paymentProviderRestClient;
        this.properties = properties;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    /**
     * Charges the provider for the given payment, retrying transient failures per the policy.
     * Returns once a terminal outcome (SUCCESS / DECLINED) is reached or attempts are exhausted.
     */
    public ProviderCallResult charge(Payment payment) {
        ProviderChargeRequest request = toRequest(payment);
        List<ProviderAttemptRecord> attempts = new ArrayList<>();

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            if (!waitBefore(attempt)) {
                break; // interrupted while backing off — stop retrying with what we have
            }

            ProviderAttemptRecord record = attemptCharge(payment, request, attempt);
            attempts.add(record);

            if (isResolved(record.outcome())) {
                break; // SUCCESS or DECLINED — never retried
            }
            log.warn("Transient provider outcome, will retry if attempts remain: paymentId={}, attempt={}/{}, outcome={}",
                    payment.getPaymentId(), attempt, properties.maxAttempts(), record.outcome());
        }

        return new ProviderCallResult(List.copyOf(attempts));
    }

    private ProviderAttemptRecord attemptCharge(Payment payment, ProviderChargeRequest request, int attemptNumber) {
        Instant started = clock.instant();
        try {
            HttpOutcome http = restClient.post()
                    .uri(PAYMENTS_PATH)
                    .headers(headers -> applyHeaders(headers, payment))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, res) ->
                            new HttpOutcome(res.getStatusCode().value(), res.bodyTo(ProviderChargeResponse.class)));

            AttemptOutcome outcome = classify(http.status());
            ProviderChargeResponse body = http.body();
            ProviderResponseRecord response = new ProviderResponseRecord(
                    http.status(),
                    body == null ? null : body.status(),
                    body == null ? null : body.transactionId(),
                    body == null ? null : body.reason(),
                    clock.instant());
            String errorDetail = (outcome == AttemptOutcome.SUCCESS) ? null
                    : "Provider HTTP " + http.status()
                      + (body != null && body.reason() != null ? ": " + body.reason() : "");
            return new ProviderAttemptRecord(attemptNumber, outcome, started, clock.instant(), errorDetail, response);

        } catch (ResourceAccessException e) {
            // Read/connect failure. A socket timeout is a TIMEOUT outcome; other I/O is transient.
            boolean timedOut = e.getCause() instanceof SocketTimeoutException;
            AttemptOutcome outcome = timedOut ? AttemptOutcome.TIMEOUT : AttemptOutcome.TRANSIENT_ERROR;
            log.warn("Provider call I/O failure: paymentId={}, attempt={}, timedOut={}, cause={}",
                    payment.getPaymentId(), attemptNumber, timedOut, e.getMessage());
            return new ProviderAttemptRecord(attemptNumber, outcome, started, clock.instant(),
                    e.getMessage(), null);
        } catch (RestClientException e) {
            log.warn("Provider call error: paymentId={}, attempt={}, cause={}",
                    payment.getPaymentId(), attemptNumber, e.getMessage());
            return new ProviderAttemptRecord(attemptNumber, AttemptOutcome.TRANSIENT_ERROR, started,
                    clock.instant(), e.getMessage(), null);
        }
    }

    private void applyHeaders(HttpHeaders headers, Payment payment) {
        headers.add(IDEMPOTENCY_KEY_HEADER, payment.getPaymentId().toString());
        if (properties.scenario() != null && !properties.scenario().isBlank()) {
            headers.add(SCENARIO_HEADER, properties.scenario());
        }
    }

    /** Maps the provider HTTP status to an attempt outcome (service.md §Fake Payment Provider). */
    private AttemptOutcome classify(int httpStatus) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return AttemptOutcome.SUCCESS;
        }
        if (httpStatus == 402 || httpStatus == 422) {
            return AttemptOutcome.DECLINED; // business failure — never retried
        }
        // 503 and any other non-success status are treated as transient and retried.
        return AttemptOutcome.TRANSIENT_ERROR;
    }

    private boolean isResolved(AttemptOutcome outcome) {
        return outcome == AttemptOutcome.SUCCESS || outcome == AttemptOutcome.DECLINED;
    }

    /** Applies the configured backoff before {@code attemptNumber}; returns false if interrupted. */
    private boolean waitBefore(int attemptNumber) {
        Duration wait = properties.backoffBefore(attemptNumber);
        if (wait.isZero() || wait.isNegative()) {
            return true;
        }
        try {
            sleeper.sleep(wait);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ProviderChargeRequest toRequest(Payment payment) {
        Money amount = payment.getAmount();
        return new ProviderChargeRequest(
                payment.getPaymentId(),
                payment.getBookingId(),
                new MoneyDto(amount.getAmount(), amount.getCurrency()));
    }

    /** Internal holder for one HTTP exchange (status + parsed body, body may be null). */
    private record HttpOutcome(int status, ProviderChargeResponse body) {}
}
