package com.atlas.payment.messaging;

import com.atlas.payment.service.InventoryReservedCommand;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.support.PaymentTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static com.atlas.payment.support.PaymentTestData.BOOKING_ID;
import static com.atlas.payment.support.PaymentTestData.EVENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock PaymentService paymentService;

    @InjectMocks PaymentEventConsumer consumer;

    @Test
    void onInventoryReserved_extracts_amount_and_saga_metadata() {
        Map<String, Object> envelope = Map.of(
                "eventId", EVENT_ID.toString(),
                "correlationId", PaymentTestData.CORRELATION_ID,
                "sagaId", PaymentTestData.SAGA_ID,
                "payload", Map.of(
                        "bookingId", BOOKING_ID.toString(),
                        "amount", Map.of("amount", 800.00, "currency", "USD")));

        consumer.onInventoryReserved(envelope);

        ArgumentCaptor<InventoryReservedCommand> command = ArgumentCaptor.forClass(InventoryReservedCommand.class);
        verify(paymentService).onInventoryReserved(eq(EVENT_ID), command.capture());
        InventoryReservedCommand cmd = command.getValue();
        assertThat(cmd.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(cmd.amount().getAmount()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(cmd.amount().getCurrency()).isEqualTo("USD");
        assertThat(cmd.correlationId()).isEqualTo(PaymentTestData.CORRELATION_ID);
        assertThat(cmd.sagaId()).isEqualTo(PaymentTestData.SAGA_ID);
    }

    @Test
    void onInventoryReserved_missing_amount_is_rejected() {
        Map<String, Object> envelope = Map.of(
                "eventId", EVENT_ID.toString(),
                "payload", Map.of("bookingId", BOOKING_ID.toString()));

        assertThatThrownBy(() -> consumer.onInventoryReserved(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void onInventoryReserved_missing_eventId_is_rejected() {
        Map<String, Object> envelope = Map.of(
                "payload", Map.of(
                        "bookingId", BOOKING_ID.toString(),
                        "amount", Map.of("amount", 800.00, "currency", "USD")));

        assertThatThrownBy(() -> consumer.onInventoryReserved(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }
}
