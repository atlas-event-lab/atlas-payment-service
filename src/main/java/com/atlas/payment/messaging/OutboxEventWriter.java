package com.atlas.payment.messaging;

import com.atlas.payment.entity.OutboxEvent;
import com.atlas.payment.repository.OutboxRepository;
import com.atlas.payment.shared.messaging.EventEnvelope;
import com.atlas.payment.shared.web.CorrelationIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes domain events to the Transactional Outbox (EVT-009).
 * <p>
 * Called from inside a {@code @Transactional} service method so the outbox row is committed
 * atomically with the payment state change — no Kafka call happens here, avoiding the dual-write
 * (coding-standards §Outbox & Event Publishing). The {@code OutboxRelay} publishes the row
 * afterwards. All Payment events are keyed by {@code bookingId} so a booking's payment events keep
 * their order on one partition (partitioning.md).
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final String PRODUCER = "payment-service";
    private static final String AGGREGATE_TYPE = "Payment";
    private static final int EVENT_VERSION = 1;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Builds the full event envelope (message-envelope.md) and stores it as a PENDING outbox row.
     *
     * @param aggregateId   the Kafka partition key — {@code bookingId} (partitioning.md)
     * @param eventType     event name, e.g. {@code PaymentSucceeded}
     * @param correlationId correlation id propagated through the saga (OBS-002)
     * @param sagaId        saga instance id (OBS-003)
     * @param payload       the business payload (never null, never carries metadata)
     */
    public void write(UUID aggregateId, String eventType,
                      String correlationId, String sagaId, Object payload) {
        var envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                EVENT_VERSION,
                Instant.now(),
                resolveTraceId(),
                correlationId,
                sagaId,
                PRODUCER,
                payload);

        outboxRepository.save(
            new OutboxEvent(
                UUID.randomUUID(),
                AGGREGATE_TYPE,
                aggregateId,
                eventType,
                EVENT_VERSION,
                serialize(envelope))
        );
    }

    private String serialize(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event envelope for outbox: eventType=" + envelope.eventType(), e);
        }
    }

    /** Reads traceId from MDC (set by {@link CorrelationIdFilter}), falls back to a new UUID. */
    private String resolveTraceId() {
        String traceId = MDC.get(CorrelationIdFilter.TRACE_ID_MDC_KEY);
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }
}
