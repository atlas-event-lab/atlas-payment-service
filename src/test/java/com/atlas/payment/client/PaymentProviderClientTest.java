package com.atlas.payment.client;

import com.atlas.payment.entity.AttemptOutcome;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.support.PaymentTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the provider call retry policy (services/payment/service.md §Provider Call): transient
 * failures (timeout, 503) are retried up to maxAttempts; business failures (DECLINED) are never
 * retried. Backoff sleeps are stubbed out, so the test is deterministic with no real waits.
 */
class PaymentProviderClientTest {

    private static final String BASE_URL = "http://provider.test";
    private static final String PAYMENTS_URL = BASE_URL + "/payments";

    private MockRestServiceServer server;
    private PaymentProviderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        PaymentProviderProperties properties = new PaymentProviderProperties(
                BASE_URL,
                Duration.ofSeconds(5),
                3,
                List.of(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(5)),
                null);
        Clock clock = Clock.fixed(PaymentTestData.T0, ZoneOffset.UTC);

        client = new PaymentProviderClient(restClient, properties, noOpSleeper(), clock);
    }

    @Test
    void success_on_first_attempt_sends_idempotency_key() {
        server.expect(times(1), requestTo(PAYMENTS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", PaymentTestData.PAYMENT_ID.toString()))
                .andRespond(withSuccess("{\"status\":\"APPROVED\",\"transactionId\":\"tx-1\"}",
                        MediaType.APPLICATION_JSON));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(result.finalAttempt().response().transactionId()).isEqualTo("tx-1");
        server.verify();
    }

    @Test
    void declined_is_not_retried() {
        server.expect(times(1), requestTo(PAYMENTS_URL))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"DECLINED\",\"reason\":\"Card declined\"}"));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(1);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.DECLINED);
        assertThat(result.finalAttempt().response().reason()).isEqualTo("Card declined");
        server.verify();
    }

    @Test
    void transient_503_is_retried_up_to_max_attempts() {
        server.expect(times(3), requestTo(PAYMENTS_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.TRANSIENT_ERROR);
        server.verify();
    }

    @Test
    void transient_then_success_recovers_without_exhausting_attempts() {
        server.expect(times(1), requestTo(PAYMENTS_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(times(1), requestTo(PAYMENTS_URL))
                .andRespond(withSuccess("{\"status\":\"APPROVED\",\"transactionId\":\"tx-9\"}",
                        MediaType.APPLICATION_JSON));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(2);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        server.verify();
    }

    @Test
    void persistent_timeout_is_retried_then_classified_as_timeout() {
        server.expect(times(3), requestTo(PAYMENTS_URL))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        ProviderCallResult result = client.charge(payment());

        assertThat(result.attempts()).hasSize(3);
        assertThat(result.finalOutcome()).isEqualTo(AttemptOutcome.TIMEOUT);
        server.verify();
    }

    private Payment payment() {
        return PaymentTestData.processingPayment();
    }

    private Sleeper noOpSleeper() {
        return duration -> { /* deterministic: no real backoff wait in tests */ };
    }
}
