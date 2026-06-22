package com.atlas.payment.service;

import com.atlas.payment.client.ProviderAttemptRecord;
import com.atlas.payment.client.ProviderCallResult;
import com.atlas.payment.client.ProviderResponseRecord;
import com.atlas.payment.entity.ConsumedEvent;
import com.atlas.payment.entity.Money;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentAttempt;
import com.atlas.payment.entity.PaymentProviderResponse;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.event.MoneyEvent;
import com.atlas.payment.event.PaymentEventPayload;
import com.atlas.payment.event.PaymentEventTypes;
import com.atlas.payment.messaging.OutboxEventWriter;
import com.atlas.payment.repository.ConsumedEventRepository;
import com.atlas.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The two transactional units of work of a payment (services/payment/service.md,
 * services/payment/state_machine.md). Each method is {@code @Transactional}: the state change and
 * the produced event commit together via the outbox (no dual-write, EVT-009). Both are idempotent —
 * {@link #beginProcessing} dedupes the trigger event and {@link #resolve} no-ops once the payment is
 * terminal — so a re-delivered event or a duplicate provider call never causes a second transition
 * or a double charge (EVT-005, EVT-008, EVT-010).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private static final String CONSUMED_EVENT_TYPE = "InventoryReserved";

    private final PaymentRepository paymentRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final OutboxEventWriter outboxEventWriter;

    /**
     * TX1 — dedupe, create the Payment, transition CREATED → PROCESSING and emit
     * {@code PaymentRequested}. Returns the persisted Payment to charge, or empty when the event was
     * already consumed or a payment already exists for the booking (idempotency).
     */
    @Transactional
    public Optional<Payment> beginProcessing(UUID eventId, InventoryReservedCommand command) {
        if (consumedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate InventoryReserved: eventId={}, bookingId={}",
                    eventId, command.bookingId());
            return Optional.empty();
        }

        Optional<Payment> existing = paymentRepository.findByBookingId(command.bookingId());
        if (existing.isPresent()) {
            // A payment already exists for this booking (e.g. a re-trigger with a new eventId).
            // Record the event as consumed and do not charge again (EVT-008).
            consumedEventRepository.save(new ConsumedEvent(eventId, CONSUMED_EVENT_TYPE));
            log.info("Payment already exists for booking, not charging again: bookingId={}, paymentId={}",
                    command.bookingId(), existing.get().getPaymentId());
            return Optional.empty();
        }

        Payment payment = new Payment(
            UUID.randomUUID(),
            command.bookingId(),
            new Money(command.amount(), "USD"),
            command.correlationId(),
            command.sagaId()
        );

        PaymentStateTransitionGuard.assertAllowed(payment.getStatus(), PaymentStatus.PROCESSING);
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        consumedEventRepository.save(new ConsumedEvent(eventId, CONSUMED_EVENT_TYPE));

        outboxEventWriter.write(payment.getBookingId(), PaymentEventTypes.PAYMENT_REQUESTED,
                command.correlationId(), command.sagaId(),
                payloadOf(payment, PaymentStatus.PROCESSING, null));

        log.info("Payment processing started: paymentId={}, bookingId={}",
                payment.getPaymentId(), payment.getBookingId());
        return Optional.of(payment);
    }

    /**
     * TX2 — persist the attempt history and transition PROCESSING → terminal, emitting the matching
     * terminal event. No-ops if the payment is already terminal (duplicate resolve).
     */
    @Transactional
    public void resolve(UUID paymentId, ProviderCallResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment vanished mid-flight: " + paymentId));

        if (payment.isTerminal()) {
            log.info("Payment already resolved, ignoring duplicate outcome: paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return;
        }

        result.attempts().forEach(record -> payment.addAttempt(toAttempt(record)));

        Terminal terminal = terminalFor(result);
        PaymentStateTransitionGuard.assertAllowed(payment.getStatus(), terminal.status());
        payment.setStatus(terminal.status());
        payment.setReason(terminal.reason());
        payment.setProviderTransactionId(terminal.transactionId());
        paymentRepository.save(payment);

        outboxEventWriter.write(payment.getBookingId(), terminal.eventType(),
                payment.getCorrelationId(), payment.getSagaId(),
                payloadOf(payment, terminal.status(), terminal.reason()));

        log.info("Payment resolved: paymentId={}, bookingId={}, status={}, attempts={}",
                paymentId, payment.getBookingId(), terminal.status(), result.attempts().size());
    }

    /** Maps the final provider outcome to the terminal state, event type and denormalized fields. */
    private Terminal terminalFor(ProviderCallResult result) {
        ProviderAttemptRecord last = result.finalAttempt();
        int attempts = result.attempts().size();
        return switch (result.finalOutcome()) {
            case SUCCESS -> new Terminal(PaymentStatus.SUCCEEDED, PaymentEventTypes.PAYMENT_SUCCEEDED,
                    null, last.response() == null ? null : last.response().transactionId());
            case DECLINED -> new Terminal(PaymentStatus.FAILED, PaymentEventTypes.PAYMENT_FAILED,
                    declineReason(last), null);
            case TRANSIENT_ERROR -> new Terminal(PaymentStatus.FAILED, PaymentEventTypes.PAYMENT_FAILED,
                    "Provider unavailable after " + attempts + " attempt(s)", null);
            case TIMEOUT -> new Terminal(PaymentStatus.TIMED_OUT, PaymentEventTypes.PAYMENT_TIMED_OUT,
                    "Provider did not respond after " + attempts + " attempt(s)", null);
        };
    }

    private String declineReason(ProviderAttemptRecord last) {
        if (last.response() != null && last.response().reason() != null) {
            return last.response().reason();
        }
        return "Declined by provider";
    }

    private PaymentAttempt toAttempt(ProviderAttemptRecord record) {
        PaymentAttempt attempt = new PaymentAttempt(UUID.randomUUID(), record.attemptNumber(),
                record.outcome(), record.startedAt(), record.completedAt(), record.errorDetail());
        ProviderResponseRecord response = record.response();
        if (response != null) {
            attempt.attachResponse(new PaymentProviderResponse(UUID.randomUUID(), response.httpStatus(),
                    response.providerStatus(), response.transactionId(), response.reason(),
                    response.receivedAt()));
        }
        return attempt;
    }

    private PaymentEventPayload payloadOf(Payment payment, PaymentStatus status, String reason) {
        Money amount = payment.getAmount();
        return new PaymentEventPayload(
                payment.getPaymentId(),
                payment.getBookingId(),
                new MoneyEvent(amount.getAmount(), amount.getCurrency()),
                status,
                reason);
    }

    /** Terminal resolution: target state, event type, optional reason and provider transaction id. */
    private record Terminal(PaymentStatus status, String eventType, String reason, String transactionId) {}
}
