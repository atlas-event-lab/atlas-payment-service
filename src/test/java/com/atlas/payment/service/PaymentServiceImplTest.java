package com.atlas.payment.service;

import com.atlas.payment.client.PaymentProviderClient;
import com.atlas.payment.client.ProviderCallResult;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.repository.PaymentRepository;
import com.atlas.payment.support.PaymentTestData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.atlas.payment.support.PaymentTestData.EVENT_ID;
import static com.atlas.payment.support.PaymentTestData.PAYMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentTransactionService transactionService;
    @Mock PaymentProviderClient providerClient;
    @Mock PaymentRepository paymentRepository;

    // Real registry (not a mock) so the provider-call / recovery counters can be asserted
    // (ADR-0020 / ADR-0021).
    MeterRegistry meterRegistry;
    PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new PaymentServiceImpl(
                transactionService, providerClient, paymentRepository, meterRegistry);
    }

    // ── onInventoryReserved ───────────────────────────────────────────────────

    @Test
    void onInventoryReserved_charges_and_resolves() {
        Payment payment = PaymentTestData.processingPayment();
        ProviderCallResult result = successResult();
        when(transactionService.beginProcessing(eq(EVENT_ID), any())).thenReturn(Optional.of(payment));
        when(providerClient.charge(payment)).thenReturn(result);

        service.onInventoryReserved(EVENT_ID, command());

        verify(transactionService).resolve(PAYMENT_ID, result);
        assertThat(providerCalls("success")).isEqualTo(1.0);
    }

    @Test
    void onInventoryReserved_skipped_beginProcessing_never_charges() {
        when(transactionService.beginProcessing(eq(EVENT_ID), any())).thenReturn(Optional.empty());

        service.onInventoryReserved(EVENT_ID, command());

        verifyNoInteractions(providerClient);
        verify(transactionService, never()).resolve(any(), any());
    }

    // ── recoverStalePayment (ADR-0021) ────────────────────────────────────────

    @Test
    void recoverStalePayment_recharges_same_payment_and_resolves() {
        Payment stale = PaymentTestData.processingPayment();
        ProviderCallResult result = successResult();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(stale));
        when(providerClient.charge(stale)).thenReturn(result);

        service.recoverStalePayment(PAYMENT_ID);

        // Same Payment (same paymentId → same Idempotency-Key): never a second charge intent.
        verify(providerClient).charge(stale);
        verify(transactionService).resolve(PAYMENT_ID, result);
        assertThat(recoveries("success")).isEqualTo(1.0);
        assertThat(providerCalls("success")).isEqualTo(1.0);
    }

    @Test
    void recoverStalePayment_noop_when_payment_vanished() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        service.recoverStalePayment(PAYMENT_ID);

        verifyNoInteractions(providerClient);
        verify(transactionService, never()).resolve(any(), any());
        assertThat(recoveries("success")).isEqualTo(0.0);
    }

    @Test
    void recoverStalePayment_noop_when_already_terminal() {
        Payment resolved = PaymentTestData.processingPayment();
        resolved.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(resolved));

        service.recoverStalePayment(PAYMENT_ID);

        verifyNoInteractions(providerClient);
        verify(transactionService, never()).resolve(any(), any());
    }

    @Test
    void recoverStalePayment_timeout_outcome_is_tagged() {
        Payment stale = PaymentTestData.processingPayment();
        ProviderCallResult result = new ProviderCallResult(List.of(
                PaymentTestData.timeoutAttempt(1),
                PaymentTestData.timeoutAttempt(2),
                PaymentTestData.timeoutAttempt(3)));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(stale));
        when(providerClient.charge(stale)).thenReturn(result);

        service.recoverStalePayment(PAYMENT_ID);

        verify(transactionService).resolve(PAYMENT_ID, result);
        assertThat(recoveries("timeout")).isEqualTo(1.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ProviderCallResult successResult() {
        return new ProviderCallResult(List.of(PaymentTestData.successAttempt(1, "txn-1")));
    }

    private InventoryReservedCommand command() {
        return new InventoryReservedCommand(PaymentTestData.BOOKING_ID,
                PaymentTestData.defaultAmount().getAmount(),
                PaymentTestData.CORRELATION_ID, PaymentTestData.SAGA_ID);
    }

    private double providerCalls(String outcome) {
        return meterRegistry.counter("atlas.payment.provider.calls", "outcome", outcome).count();
    }

    private double recoveries(String outcome) {
        return meterRegistry.counter("atlas.payment.recoveries", "outcome", outcome).count();
    }
}
