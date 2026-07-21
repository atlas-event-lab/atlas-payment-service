package com.atlas.payment.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.repository.PaymentRepository;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.support.PaymentTestData;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentRecoverySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-06-21T12:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofMinutes(3);
    private static final Instant CUTOFF = NOW.minus(STALE_AFTER);

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    PaymentService paymentService;

    private PaymentRecoveryScheduler newScheduler() {
        return new PaymentRecoveryScheduler(
                paymentRepository,
                paymentService,
                new PaymentRecoveryProperties(STALE_AFTER),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recoverStalePayments_recovers_each_stale_PROCESSING() {
        Payment stale = PaymentTestData.processingPayment();
        when(paymentRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        eq(PaymentStatus.PROCESSING), eq(CUTOFF)))
                .thenReturn(List.of(stale));

        newScheduler().recoverStalePayments();

        verify(paymentService).recoverStalePayment(stale.getPaymentId());
    }

    @Test
    void recoverStalePayments_noStale_doesNothing() {
        when(paymentRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        eq(PaymentStatus.PROCESSING), eq(CUTOFF)))
                .thenReturn(List.of());

        newScheduler().recoverStalePayments();

        verify(paymentService, never()).recoverStalePayment(any());
    }

    @Test
    void recoverStalePayments_failureOnOne_continuesBatch() {
        Payment p1 = PaymentTestData.processingPayment();
        Payment p2 = new Payment(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentTestData.defaultAmount(),
                PaymentTestData.CORRELATION_ID,
                PaymentTestData.SAGA_ID);
        p2.setStatus(PaymentStatus.PROCESSING);
        when(paymentRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        eq(PaymentStatus.PROCESSING), eq(CUTOFF)))
                .thenReturn(List.of(p1, p2));
        doThrow(new RuntimeException("boom")).when(paymentService).recoverStalePayment(p1.getPaymentId());

        newScheduler().recoverStalePayments();

        verify(paymentService, times(1)).recoverStalePayment(p2.getPaymentId());
    }
}
