package com.atlas.payment.scheduler;

import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.repository.PaymentRepository;
import com.atlas.payment.service.PaymentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety-net sweeper that re-drives payments stuck in {@code PROCESSING} (ADR-0021).
 * <p>
 * A crash between TX1 ({@code beginProcessing}) and TX2 ({@code resolve}) leaves a payment in
 * PROCESSING with no owner: the redelivered {@code InventoryReserved} is deduped (its
 * {@code eventId} committed with TX1), Booking's expiration owns {@code PENDING} only, and the
 * {@code PaymentTimedOut} that owns {@code INVENTORY_RESERVED} died with the process. This sweep
 * finds those orphans and resumes the flow exactly where it was cut: provider charge with the
 * same {@code Idempotency-Key} (= {@code paymentId} — the provider replays the original outcome,
 * so a re-drive can never double-charge), then the normal TX2 resolve.
 * <p>
 * Idempotent and stateless (ARCH-010): the deadline derives from the persisted
 * {@code updatedAt}; {@code fixedDelay} prevents overlapping runs within an instance; a race
 * with a live consumer or a concurrent sweep is absorbed by the terminal no-op in
 * {@code resolve} and by the provider idempotency key. Per-payment failures are isolated so one
 * bad payment does not abort the batch (same convention as Inventory's TTL sweep).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentRecoveryProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${atlas.payment.recovery.sweep-interval-ms:60000}")
    public void recoverStalePayments() {
        Instant cutoff = clock.instant().minus(properties.staleAfter());
        List<Payment> stale = paymentRepository.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PaymentStatus.PROCESSING, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.warn("Recovering {} stale PROCESSING payment(s) older than {}", stale.size(), cutoff);
        for (Payment payment : stale) {
            try {
                paymentService.recoverStalePayment(payment.getPaymentId());
            } catch (Exception e) {
                // Isolate per-payment failures so the rest of the batch still proceeds.
                log.error("Failed to recover payment: paymentId={}", payment.getPaymentId(), e);
            }
        }
    }
}
