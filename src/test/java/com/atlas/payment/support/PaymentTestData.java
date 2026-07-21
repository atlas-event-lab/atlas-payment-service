package com.atlas.payment.support;

import com.atlas.payment.client.ProviderAttemptRecord;
import com.atlas.payment.client.ProviderResponseRecord;
import com.atlas.payment.entity.AttemptOutcome;
import com.atlas.payment.entity.Money;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shared fixtures for Payment unit tests. */
public final class PaymentTestData {

    public static final UUID EVENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID BOOKING_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID PAYMENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final String CORRELATION_ID = "corr-123";
    public static final String SAGA_ID = "saga-456";

    public static final Instant T0 = Instant.parse("2026-06-21T00:00:00Z");
    public static final Instant T1 = Instant.parse("2026-06-21T00:00:01Z");

    private PaymentTestData() {}

    public static Money money(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money defaultAmount() {
        return money("800.00", "USD");
    }

    /** A Payment already moved to PROCESSING (the state in which the provider is charged). */
    public static Payment processingPayment() {
        Payment payment = new Payment(PAYMENT_ID, BOOKING_ID, defaultAmount(), CORRELATION_ID, SAGA_ID);
        payment.setStatus(PaymentStatus.PROCESSING);
        return payment;
    }

    public static ProviderAttemptRecord successAttempt(int n, String transactionId) {
        return new ProviderAttemptRecord(
                n,
                AttemptOutcome.SUCCESS,
                T0,
                T1,
                null,
                new ProviderResponseRecord(200, "APPROVED", transactionId, null, T1));
    }

    public static ProviderAttemptRecord declinedAttempt(int n, String reason) {
        return new ProviderAttemptRecord(
                n,
                AttemptOutcome.DECLINED,
                T0,
                T1,
                "Provider HTTP 402",
                new ProviderResponseRecord(402, "DECLINED", null, reason, T1));
    }

    public static ProviderAttemptRecord transientAttempt(int n) {
        return new ProviderAttemptRecord(
                n,
                AttemptOutcome.TRANSIENT_ERROR,
                T0,
                T1,
                "Provider HTTP 503",
                new ProviderResponseRecord(503, "UNAVAILABLE", null, null, T1));
    }

    public static ProviderAttemptRecord timeoutAttempt(int n) {
        return new ProviderAttemptRecord(n, AttemptOutcome.TIMEOUT, T0, T1, "read timed out", null);
    }
}
