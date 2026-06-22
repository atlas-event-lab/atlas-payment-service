package com.atlas.payment.messaging;

import com.atlas.payment.entity.OutboxEvent;
import com.atlas.payment.entity.OutboxStatus;
import com.atlas.payment.event.PaymentEventTypes;
import com.atlas.payment.repository.OutboxRepository;
import com.atlas.payment.shared.messaging.EventTopics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Phase-1 outbox relay (EVT-009): a scheduled poller that publishes outbox rows to Kafka.
 * <p>
 * Reads PENDING / FAILED rows oldest-first, publishes each envelope keyed by {@code aggregateId}
 * ({@code bookingId}), and marks it PUBLISHED. A failed publish is marked FAILED and retried on a
 * later poll. Delivery is at-least-once — consumers deduplicate on the envelope {@code eventId}
 * (coding-standards §Outbox & Event Publishing).
 * <p>
 * Idempotent and uses {@code fixedDelay}, so a slow run never overlaps the next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final List<OutboxStatus> UNPUBLISHED =
            List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${atlas.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByStatusInOrderByCreatedAtAsc(UNPUBLISHED);
        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox relay processing {} event(s)", batch.size());
        for (OutboxEvent event : batch) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            String topic = resolveTopic(event.getEventType());

            // Block until the broker acknowledges so the row is only marked PUBLISHED on success.
            kafkaTemplate.send(topic, event.getAggregateId().toString(), envelope).get();

            event.markPublished(Instant.now());
            outboxRepository.save(event);
            log.info("Outbox event published: id={}, eventType={}, aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(event, e);
        } catch (Exception e) {
            markFailed(event, e);
        }
    }

    private void markFailed(OutboxEvent event, Exception e) {
        event.markFailed();
        outboxRepository.save(event);
        log.error("Failed to publish outbox event: id={}, eventType={}, attempts={}",
                event.getId(), event.getEventType(), event.getAttempts(), e);
    }

    /** Maps an event type to its owning Payment topic (topics.md, payment-events.yaml). */
    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case PaymentEventTypes.PAYMENT_REQUESTED  -> EventTopics.PAYMENT_REQUESTED;
            case PaymentEventTypes.PAYMENT_SUCCEEDED  -> EventTopics.PAYMENT_SUCCEEDED;
            case PaymentEventTypes.PAYMENT_FAILED     -> EventTopics.PAYMENT_FAILED;
            case PaymentEventTypes.PAYMENT_TIMED_OUT  -> EventTopics.PAYMENT_TIMED_OUT;
            default -> throw new IllegalStateException("No topic mapping for event type: " + eventType);
        };
    }
}
