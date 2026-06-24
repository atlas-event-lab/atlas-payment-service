package com.atlas.payment.messaging;

import com.atlas.payment.event.EventValidator;
import com.atlas.payment.event.InventoryReservedPayload;
import com.atlas.payment.exception.InvalidPaymentStateTransitionException;
import com.atlas.payment.service.InventoryReservedCommand;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.event.EventEnvelope;
import com.atlas.payment.shared.messaging.EventTopics;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

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
    private final EventValidator eventValidator;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "false",
            exclude = {InvalidPaymentStateTransitionException.class, IllegalArgumentException.class,
                    ConstraintViolationException.class}
    )
    @KafkaListener(topics = EventTopics.INVENTORY_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReserved(EventEnvelope<InventoryReservedPayload> envelope) {
        eventValidator.validate(envelope);
        UUID eventId = envelope.eventId();
        InventoryReservedPayload payload = envelope.payload();

        InventoryReservedCommand command = new InventoryReservedCommand(
                payload.bookingId(),
                payload.total(),
                envelope.correlationId(),
                envelope.sagaId());

        log.info("Received InventoryReserved: eventId={}, bookingId={}", eventId, command.bookingId());
        paymentService.onInventoryReserved(eventId, command);
    }
}
