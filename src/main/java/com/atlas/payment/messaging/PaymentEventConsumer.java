package com.atlas.payment.messaging;

import com.atlas.payment.exception.InvalidPaymentStateTransitionException;
import com.atlas.payment.service.InventoryReservedCommand;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.shared.messaging.EventTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for the Payment side of the booking choreography saga (services/payment/service.md).
 * <p>
 * {@code InventoryReserved} is the single trigger: it carries the {@code bookingId} and the
 * {@code amount} to charge, so Payment needs no other event and never reads the Booking DB
 * (ARCH-003). Processing is idempotent on the envelope {@code eventId} (EVT-005, EVT-010).
 * <p>
 * Retry strategy (retry-strategy.md): 4 total attempts (1 initial + 3 retries) via Retry Topics,
 * delays 5s → 30s → 120s → DLQ. Malformed envelopes ({@link IllegalArgumentException}) and forbidden
 * transitions ({@link InvalidPaymentStateTransitionException}) go straight to the DLQ
 * (dlq-strategy.md). Note: this Kafka-consumer retry is for delivery/processing faults; the
 * provider-call timeout/retry is a separate, inner policy owned by the provider client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final long RETRY_DELAY_MS = 5_000L;
    private static final double RETRY_MULTIPLIER = 6.0;
    private static final long RETRY_MAX_DELAY_MS = 120_000L;

    private final PaymentService paymentService;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            exclude = {InvalidPaymentStateTransitionException.class, IllegalArgumentException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_BOOKING_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReserved(Map<String, Object> envelope) {
        UUID eventId = extractEventId(envelope);
        Map<String, Object> payload = extractPayload(envelope);

        InventoryReservedCommand command = new InventoryReservedCommand(
                extractUuid(payload, "bookingId"),
                extractTotalAmount(payload),
                stringOrNull(envelope.get("correlationId")),
                stringOrNull(envelope.get("sagaId")));

        log.info("Received InventoryReserved: eventId={}, bookingId={}", eventId, command.bookingId());
        paymentService.onInventoryReserved(eventId, command);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID extractEventId(Map<String, Object> envelope) {
        Object raw = envelope.get("eventId");
        if (raw == null) {
            throw new IllegalArgumentException("Missing eventId in envelope");
        }
        return UUID.fromString(raw.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        Object raw = envelope.get("payload");
        if (!(raw instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Missing payload in envelope");
        }
        return (Map<String, Object>) raw;
    }

    private BigDecimal extractTotalAmount(Map<String, Object> payload) {
        Object total = payload.get("total");
        if (total == null) {
            throw new IllegalArgumentException("Missing 'total' in InventoryReserved payload");
        }

        return new BigDecimal(total.toString());
    }

    private UUID extractUuid(Map<String, Object> payload, String field) {
        Object raw = payload.get(field);
        if (raw == null) {
            throw new IllegalArgumentException("Missing field '" + field + "' in payload");
        }
        return UUID.fromString(raw.toString());
    }

    private String stringOrNull(Object value) {
        return value == null ? null : value.toString();
    }
}
