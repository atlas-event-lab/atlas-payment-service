package com.atlas.payment.service;

import com.atlas.payment.client.ProviderCallResult;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.event.PaymentEventPayload;
import com.atlas.payment.event.PaymentEventTypes;
import com.atlas.payment.messaging.OutboxEventWriter;
import com.atlas.payment.repository.ConsumedEventRepository;
import com.atlas.payment.repository.PaymentRepository;
import com.atlas.payment.support.PaymentTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.atlas.payment.support.PaymentTestData.BOOKING_ID;
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
class PaymentTransactionServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock ConsumedEventRepository consumedEventRepository;
    @Mock OutboxEventWriter outboxEventWriter;

    @InjectMocks PaymentTransactionService service;

    // ── beginProcessing ───────────────────────────────────────────────────────

    @Test
    void beginProcessing_creates_payment_in_processing_and_emits_PaymentRequested() {
        when(consumedEventRepository.existsById(EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

        Optional<Payment> result = service.beginProcessing(EVENT_ID, command());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        verify(paymentRepository).save(any(Payment.class));
        verify(consumedEventRepository).save(any());

        PaymentEventPayload payload = capturePayload(PaymentEventTypes.PAYMENT_REQUESTED);
        assertThat(payload.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payload.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(payload.reason()).isNull();
    }

    @Test
    void beginProcessing_skips_duplicate_event() {
        when(consumedEventRepository.existsById(EVENT_ID)).thenReturn(true);

        Optional<Payment> result = service.beginProcessing(EVENT_ID, command());

        assertThat(result).isEmpty();
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    void beginProcessing_does_not_charge_twice_for_same_booking() {
        when(consumedEventRepository.existsById(EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByBookingId(BOOKING_ID))
                .thenReturn(Optional.of(PaymentTestData.processingPayment()));

        Optional<Payment> result = service.beginProcessing(EVENT_ID, command());

        assertThat(result).isEmpty();
        verify(consumedEventRepository).save(any()); // event recorded so it is not reprocessed
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
    }

    // ── resolve ───────────────────────────────────────────────────────────────

    @Test
    void resolve_success_marks_SUCCEEDED_and_emits_PaymentSucceeded() {
        Payment payment = PaymentTestData.processingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service.resolve(PAYMENT_ID, new ProviderCallResult(List.of(
                PaymentTestData.successAttempt(1, "txn-789"))));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderTransactionId()).isEqualTo("txn-789");
        assertThat(payment.getAttempts()).hasSize(1);
        PaymentEventPayload payload = capturePayload(PaymentEventTypes.PAYMENT_SUCCEEDED);
        assertThat(payload.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void resolve_declined_marks_FAILED_with_provider_reason() {
        Payment payment = PaymentTestData.processingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service.resolve(PAYMENT_ID, new ProviderCallResult(List.of(
                PaymentTestData.declinedAttempt(1, "Insufficient funds"))));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getReason()).isEqualTo("Insufficient funds");
        PaymentEventPayload payload = capturePayload(PaymentEventTypes.PAYMENT_FAILED);
        assertThat(payload.reason()).isEqualTo("Insufficient funds");
    }

    @Test
    void resolve_persistent_transient_error_marks_FAILED() {
        Payment payment = PaymentTestData.processingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service.resolve(PAYMENT_ID, new ProviderCallResult(List.of(
                PaymentTestData.transientAttempt(1),
                PaymentTestData.transientAttempt(2),
                PaymentTestData.transientAttempt(3))));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getAttempts()).hasSize(3);
        capturePayload(PaymentEventTypes.PAYMENT_FAILED);
    }

    @Test
    void resolve_persistent_timeout_marks_TIMED_OUT() {
        Payment payment = PaymentTestData.processingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service.resolve(PAYMENT_ID, new ProviderCallResult(List.of(
                PaymentTestData.timeoutAttempt(1),
                PaymentTestData.timeoutAttempt(2),
                PaymentTestData.timeoutAttempt(3))));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.TIMED_OUT);
        capturePayload(PaymentEventTypes.PAYMENT_TIMED_OUT);
    }

    @Test
    void resolve_is_idempotent_when_already_terminal() {
        Payment payment = PaymentTestData.processingPayment();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        service.resolve(PAYMENT_ID, new ProviderCallResult(List.of(
                PaymentTestData.successAttempt(1, "txn-1"))));

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private InventoryReservedCommand command() {
        return new InventoryReservedCommand(BOOKING_ID, PaymentTestData.defaultAmount(),
                PaymentTestData.CORRELATION_ID, PaymentTestData.SAGA_ID);
    }

    private PaymentEventPayload capturePayload(String expectedEventType) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(eq(BOOKING_ID), eq(expectedEventType),
                any(), any(), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(PaymentEventPayload.class);
        return (PaymentEventPayload) payload.getValue();
    }
}
