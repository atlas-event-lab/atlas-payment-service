package com.atlas.payment.messaging;

import com.atlas.payment.event.EventValidator;
import com.atlas.payment.event.InventoryReservedPayload;
import com.atlas.payment.service.InventoryReservedCommand;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.event.EventEnvelope;
import com.atlas.payment.support.PaymentTestData;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentEventConsumerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final EventValidator eventValidator =
            new EventValidator(Validation.buildDefaultValidatorFactory().getValidator());
    private final PaymentEventConsumer consumer = new PaymentEventConsumer(paymentService, eventValidator);

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
}
