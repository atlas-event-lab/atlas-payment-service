package com.atlas.payment.messaging;

import com.atlas.payment.event.EventValidator;
import com.atlas.payment.event.InventoryReservedPayload;
import com.atlas.payment.exception.InvalidPaymentStateTransitionException;
import com.atlas.payment.service.InventoryReservedCommand;
import com.atlas.payment.service.PaymentService;
import com.atlas.payment.event.EventEnvelope;
import com.atlas.payment.shared.messaging.ConsumerEventType;
import com.atlas.payment.shared.messaging.EventTopics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
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
 * delays 5s → 30s → 120s → DLQ. Malformed envelopes ({@link IllegalArgumentException},
 * {@link ConstraintViolationException}) and forbidden transitions
 * ({@link InvalidPaymentStateTransitionException}) go straight to the DLQ (dlq-strategy.md). Note:
 * this Kafka-consumer retry is for delivery/processing faults; the provider-call timeout/retry is
 * a separate, inner policy owned by the provider client.
 * <p>
 * DLQ replay (ADR-0022, Experiment 06): the DLT handler is <b>manually started</b> —
 * {@code autoStartDltHandler} is gated on {@code atlas.payment.dlq.auto-start} (default false), so
 * parked messages stay parked until an operator deliberately enables replay (env flag + rollout).
 * When started, {@link #onInventoryReservedDlt} drains {@code inventory.reserved.dlq}: records that
 * parked on a <i>recoverable</i> (retries-exhausted) fault are re-driven through the idempotent
 * normal path; genuinely poison records (validation / forbidden transition) are quarantined, never
 * reprocessed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final long RETRY_DELAY_MS = 5_000L;
    private static final double RETRY_MULTIPLIER = 6.0;
    private static final long RETRY_MAX_DELAY_MS = 120_000L;

    private static final String M_DLQ_PARKED = "atlas.payment.dlq.parked";
    private static final String M_DLQ_REPLAYED = "atlas.payment.dlq.replayed";
    static final String REASON_VALIDATION = "validation";
    static final String REASON_FORBIDDEN_TRANSITION = "forbidden_transition";
    static final String REASON_RETRIES_EXHAUSTED = "retries_exhausted";
    static final String OUTCOME_REPROCESSED = "reprocessed";
    static final String OUTCOME_QUARANTINED = "quarantined";
    private static final String EVENT_TAG = ConsumerEventType.INVENTORY_RESERVED.name().toLowerCase();
    private static final String EVENT_KEY = "event";

    private final PaymentService paymentService;
    private final EventValidator eventValidator;
    private final MeterRegistry meterRegistry;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = RETRY_MULTIPLIER, maxDelay = RETRY_MAX_DELAY_MS),
            dltTopicSuffix = ".dlq",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoStartDltHandler = "${atlas.payment.dlq.auto-start:false}",
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

    /**
     * Manual DLQ replay (ADR-0022, dlq-strategy.md: "Manual replay is allowed"). Runs only when the
     * DLT container has been deliberately started ({@code atlas.payment.dlq.auto-start=true}).
     * Classifies why the record parked from the {@code kafka_dlt-exception-fqcn} header and either
     * re-drives it through the idempotent normal path (recoverable / retries-exhausted) or
     * quarantines it (poison — the same bytes would just re-fail). Never double-charges: the re-drive
     * goes through the {@code eventId} dedupe and the provider {@code Idempotency-Key} (EVT-005/010).
     */
    @DltHandler
    public void onInventoryReservedDlt(
            @Payload(required = false) EventEnvelope<InventoryReservedPayload> envelope,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionFqcn) {

        String reason = classifyParkReason(envelope, exceptionFqcn);
        meterRegistry.counter(M_DLQ_PARKED, "reason", reason, EVENT_KEY, EVENT_TAG).increment();

        if (!REASON_RETRIES_EXHAUSTED.equals(reason)) {
            // Poison: re-driving unchanged bytes just re-fails. Quarantine (log + count), do not reprocess.
            log.warn("DLQ replay: quarantining poison record (not reprocessing): reason={}, exception={}, eventId={}",
                    reason, exceptionFqcn, envelope == null ? null : envelope.eventId());
            meterRegistry.counter(M_DLQ_REPLAYED, "outcome", OUTCOME_QUARANTINED, EVENT_KEY, EVENT_TAG).increment();
            return;
        }

        UUID eventId = envelope.eventId();
        InventoryReservedPayload payload = envelope.payload();
        InventoryReservedCommand command = new InventoryReservedCommand(
                payload.bookingId(),
                payload.total(),
                envelope.correlationId(),
                envelope.sagaId());

        log.warn("DLQ replay: re-driving recoverable record: eventId={}, bookingId={}",
                eventId, command.bookingId());
        paymentService.onInventoryReserved(eventId, command);
        meterRegistry.counter(M_DLQ_REPLAYED, "outcome", OUTCOME_REPROCESSED, EVENT_KEY, EVENT_TAG).increment();
    }

    /**
     * Maps the original failure to a park reason. A null/undeserializable envelope, a validation
     * failure or an illegal argument are poison; a forbidden transition is its own poison reason;
     * anything else parked after exhausting the retry ladder and is recoverable.
     */
    private String classifyParkReason(EventEnvelope<InventoryReservedPayload> envelope, String exceptionFqcn) {
        if (envelope == null) {
            return REASON_VALIDATION;
        }
        if (exceptionFqcn == null) {
            return REASON_RETRIES_EXHAUSTED;
        }
        if (exceptionFqcn.equals(InvalidPaymentStateTransitionException.class.getName())) {
            return REASON_FORBIDDEN_TRANSITION;
        }
        if (exceptionFqcn.equals(ConstraintViolationException.class.getName())
                || exceptionFqcn.equals(IllegalArgumentException.class.getName())) {
            return REASON_VALIDATION;
        }
        return REASON_RETRIES_EXHAUSTED;
    }
}
