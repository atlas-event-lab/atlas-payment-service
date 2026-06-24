package com.atlas.payment.client;

import com.atlas.payment.client.dto.ProviderChargeRequest;
import com.atlas.payment.client.dto.ProviderChargeResponse;
import com.atlas.payment.entity.AttemptOutcome;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.support.PaymentTestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the provider call retry policy (services/payment/service.md §Provider Call): transient
 * failures (timeout, 503) are retried up to maxAttempts; business failures (DECLINED) are never
 * retried. The Feign client is mocked so each attempt's HTTP outcome is stubbed directly; backoff
 * sleeps are stubbed out, so the test is deterministic with no real waits.
 */
class PaymentProviderClientTest {

    private static final String PROVIDER_URL = "http://provider.test/payments";

    private PaymentProviderFeignClient feignClient;
    private PaymentProviderClient client;

    @BeforeEach
    void setUp() {
        feignClient = mock(PaymentProviderFeignClient.class);

        PaymentProviderProperties properties = new PaymentProviderProperties(
                "http://provider.test",
                Duration.ofSeconds(5),
                3,
                List.of(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                null);
        Clock clock = Clock.fixed(PaymentTestData.T0, ZoneOffset.UTC);

        client = new PaymentProviderClient(feignClient, properties, noOpSleeper(), clock, new ObjectMapper());
    }

    @Test
    void success_on_first_attempt_sends_idempotency_key() {
        when(feignClient.charge(any(), isNull(), any()))
                .thenReturn(ResponseEntity.ok(new ProviderChargeResponse("tx-1", "APPROVED", null)));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(result.finalAttempt().response().transactionId()).isEqualTo("tx-1");

        ArgumentCaptor<String> idempotencyKey = ArgumentCaptor.forClass(String.class);
        verify(feignClient).charge(idempotencyKey.capture(), isNull(), any(ProviderChargeRequest.class));
        assertThat(idempotencyKey.getValue()).isEqualTo(PaymentTestData.PAYMENT_ID.toString());
    }

    @Test
    void declined_is_not_retried() {
        when(feignClient.charge(any(), isNull(), any()))
                .thenThrow(httpError(HttpStatus.PAYMENT_REQUIRED,
                        "{\"status\":\"DECLINED\",\"reason\":\"Card declined\"}"));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.DECLINED);
        assertThat(result.finalAttempt().response().reason()).isEqualTo("Card declined");
        verify(feignClient, times(1)).charge(any(), isNull(), any());
    }

    @Test
    void transient_503_is_retried_up_to_max_attempts() {
        when(feignClient.charge(any(), isNull(), any()))
                .thenThrow(httpError(HttpStatus.SERVICE_UNAVAILABLE, null));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.TRANSIENT_ERROR);
        verify(feignClient, times(3)).charge(any(), isNull(), any());
    }

    @Test
    void transient_then_success_recovers_without_exhausting_attempts() {
        when(feignClient.charge(any(), isNull(), any()))
                .thenThrow(httpError(HttpStatus.SERVICE_UNAVAILABLE, null))
                .thenReturn(ResponseEntity.ok(new ProviderChargeResponse("tx-9", "APPROVED", null)));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(2);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        verify(feignClient, times(2)).charge(any(), isNull(), any());
    }

    @Test
    void persistent_timeout_is_retried_then_classified_as_timeout() {
        when(feignClient.charge(any(), isNull(), any()))
                .thenThrow(socketTimeout());

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.TIMEOUT);
        verify(feignClient, times(3)).charge(any(), isNull(), any());
    }

    private Payment payment() {
        return PaymentTestData.processingPayment();
    }

    /** A non-2xx HTTP response as Feign surfaces it: status carried on the exception, body optional. */
    private FeignException httpError(HttpStatus status, String body) {
        Response.Builder response = Response.builder()
                .status(status.value())
                .reason(status.getReasonPhrase())
                .request(dummyRequest())
                .headers(Collections.emptyMap());
        if (body != null) {
            response.body(body, StandardCharsets.UTF_8);
        }
        return FeignException.errorStatus("charge", response.build());
    }

    /** A read timeout as Feign surfaces it: RetryableException whose cause is a SocketTimeoutException. */
    private RetryableException socketTimeout() {
        return new RetryableException(-1, "read timed out", Request.HttpMethod.POST,
                new SocketTimeoutException("read timed out"), (Long) null, dummyRequest());
    }

    private Request dummyRequest() {
        return Request.create(Request.HttpMethod.POST, PROVIDER_URL,
                Collections.emptyMap(), Request.Body.empty(), null);
    }

    private Sleeper noOpSleeper() {
        return duration -> { /* deterministic: no real backoff wait in tests */ };
    }
}
