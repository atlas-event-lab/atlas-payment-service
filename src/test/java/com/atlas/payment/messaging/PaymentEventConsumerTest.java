package com.atlas.payment.messaging;

import com.atlas.payment.event.EventValidator;
import com.atlas.payment.event.InventoryReservedPayload;
import com.atlas.payment.exception.InvalidPaymentStateTransitionException;
import com.atlas.payment.service.InventoryReservedCommand;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.event.EventEnvelope;
import com.atlas.payment.support.PaymentTestData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.atlas.payment.support.PaymentTestData.BOOKING_ID;
import static com.atlas.payment.support.PaymentTestData.EVENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentEventConsumerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final EventValidator eventValidator =
            new EventValidator(Validation.buildDefaultValidatorFactory().getValidator());
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final PaymentEventConsumer consumer =
            new PaymentEventConsumer(paymentService, eventValidator, meterRegistry);

    private static EventEnvelope<InventoryReservedPayload> envelope(UUID eventId, InventoryReservedPayload payload) {
        return new EventEnvelope<>(
                eventId,
                "INVENTORY_RESERVED",
                1,
                Instant.now(),
                null,
                PaymentTestData.CORRELATION_ID,
                PaymentTestData.SAGA_ID,
                "inventory-service",
                payload);
    }

    private double parked(String reason) {
        return meterRegistry.counter("atlas.payment.dlq.parked",
                "reason", reason, "event", "inventory_reserved").count();
    }

    private double replayed(String outcome) {
        return meterRegistry.counter("atlas.payment.dlq.replayed",
                "outcome", outcome, "event", "inventory_reserved").count();
    }

    @Test
    void onInventoryReserved_extracts_total_and_saga_metadata() {
        EventEnvelope<InventoryReservedPayload> env = envelope(EVENT_ID,
                new InventoryReservedPayload(BOOKING_ID, new BigDecimal("800.00")));

        consumer.onInventoryReserved(env);

        ArgumentCaptor<InventoryReservedCommand> command = ArgumentCaptor.forClass(InventoryReservedCommand.class);
        verify(paymentService).onInventoryReserved(eq(EVENT_ID), command.capture());
        InventoryReservedCommand cmd = command.getValue();
        assertThat(cmd.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(cmd.amount()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(cmd.correlationId()).isEqualTo(PaymentTestData.CORRELATION_ID);
        assertThat(cmd.sagaId()).isEqualTo(PaymentTestData.SAGA_ID);
    }

    @Test
    void onInventoryReserved_missing_total_is_rejected() {
        EventEnvelope<InventoryReservedPayload> env = envelope(EVENT_ID,
                new InventoryReservedPayload(BOOKING_ID, null));

        assertThatThrownBy(() -> consumer.onInventoryReserved(env))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("total");
    }

    @Test
    void onInventoryReserved_missing_eventId_is_rejected() {
        EventEnvelope<InventoryReservedPayload> env = envelope(null,
                new InventoryReservedPayload(BOOKING_ID, new BigDecimal("800.00")));

        assertThatThrownBy(() -> consumer.onInventoryReserved(env))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("eventId");
    }

    // ── DLQ replay (ADR-0022, Experiment 06) ─────────────────────────────────────────────────

    @Test
    void dlt_retriesExhausted_isRedrivenThroughTheNormalPath() {
        EventEnvelope<InventoryReservedPayload> env = envelope(EVENT_ID,
                new InventoryReservedPayload(BOOKING_ID, new BigDecimal("800.00")));

        // A retryable failure that exhausted the ladder — any exception outside the exclude set.
        consumer.onInventoryReservedDlt(env, "org.springframework.dao.QueryTimeoutException");

        ArgumentCaptor<InventoryReservedCommand> command = ArgumentCaptor.forClass(InventoryReservedCommand.class);
        verify(paymentService).onInventoryReserved(eq(EVENT_ID), command.capture());
        assertThat(command.getValue().bookingId()).isEqualTo(BOOKING_ID);
        assertThat(parked(PaymentEventConsumer.REASON_RETRIES_EXHAUSTED)).isEqualTo(1.0);
        assertThat(replayed(PaymentEventConsumer.OUTCOME_REPROCESSED)).isEqualTo(1.0);
        assertThat(replayed(PaymentEventConsumer.OUTCOME_QUARANTINED)).isZero();
    }

    @Test
    void dlt_validationPoison_isQuarantinedNotReprocessed() {
        EventEnvelope<InventoryReservedPayload> env = envelope(EVENT_ID,
                new InventoryReservedPayload(BOOKING_ID, null)); // malformed

        consumer.onInventoryReservedDlt(env, ConstraintViolationException.class.getName());

        verify(paymentService, never()).onInventoryReserved(any(), any());
        assertThat(parked(PaymentEventConsumer.REASON_VALIDATION)).isEqualTo(1.0);
        assertThat(replayed(PaymentEventConsumer.OUTCOME_QUARANTINED)).isEqualTo(1.0);
        assertThat(replayed(PaymentEventConsumer.OUTCOME_REPROCESSED)).isZero();
    }

    @Test
    void dlt_forbiddenTransitionPoison_isQuarantined() {
        EventEnvelope<InventoryReservedPayload> env = envelope(EVENT_ID,
                new InventoryReservedPayload(BOOKING_ID, new BigDecimal("800.00")));

        consumer.onInventoryReservedDlt(env, InvalidPaymentStateTransitionException.class.getName());

        verify(paymentService, never()).onInventoryReserved(any(), any());
        assertThat(parked(PaymentEventConsumer.REASON_FORBIDDEN_TRANSITION)).isEqualTo(1.0);
        assertThat(replayed(PaymentEventConsumer.OUTCOME_QUARANTINED)).isEqualTo(1.0);
    }

    @Test
    void dlt_nullEnvelope_isQuarantinedAsPoison() {
        consumer.onInventoryReservedDlt(null, "org.apache.kafka.common.errors.SerializationException");

        verify(paymentService, never()).onInventoryReserved(any(), any());
        assertThat(parked(PaymentEventConsumer.REASON_VALIDATION)).isEqualTo(1.0);
        assertThat(replayed(PaymentEventConsumer.OUTCOME_QUARANTINED)).isEqualTo(1.0);
    }
}
