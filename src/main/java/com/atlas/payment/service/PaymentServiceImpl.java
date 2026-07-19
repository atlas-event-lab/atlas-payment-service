package com.atlas.payment.service;

import com.atlas.payment.client.PaymentProviderClient;
import com.atlas.payment.client.ProviderCallResult;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates a payment without holding a database transaction open across the provider call
 * (services/payment/service.md). The provider call (up to 5s × 3 attempts) runs <b>outside</b> any
 * transaction; the two surrounding units of work commit independently:
 * <ol>
 *   <li><b>begin</b> — dedupe, create the Payment, move CREATED → PROCESSING and emit
 *       {@code PaymentRequested} (one transaction);</li>
 *   <li><b>provider call</b> — outside any transaction;</li>
 *   <li><b>resolve</b> — move PROCESSING → terminal and emit the terminal event (one transaction).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionService transactionService;
    private final PaymentProviderClient providerClient;
    private final PaymentRepository paymentRepository;
    private final MeterRegistry meterRegistry;

    private static final String M_PROVIDER_CALLS = "atlas.payment.provider.calls";
    private static final String M_RECOVERIES = "atlas.payment.recoveries";

    @Override
    public void onInventoryReserved(UUID eventId, InventoryReservedCommand command) {
        Optional<Payment> started = transactionService.beginProcessing(eventId, command);
        if (started.isEmpty()) {
            return; // duplicate event or a payment already exists for this booking
        }
        chargeAndResolve(started.get());
    }

    @Override
    public void recoverStalePayment(UUID paymentId) {
        Optional<Payment> found = paymentRepository.findById(paymentId);
        if (found.isEmpty() || found.get().getStatus() != PaymentStatus.PROCESSING) {
            log.debug("Skipping recovery, payment no longer PROCESSING: paymentId={}", paymentId);
            return;
        }
        Payment payment = found.get();
        log.warn("Recovering stale PROCESSING payment: paymentId={}, bookingId={}",
                payment.getPaymentId(), payment.getBookingId());

        ProviderCallResult result = chargeAndResolve(payment);
        meterRegistry.counter(M_RECOVERIES,
                "outcome", result.finalOutcome().name().toLowerCase()).increment();
    }

    /** The shared post-TX1 path: provider call (outside any transaction), then TX2 resolve. */
    private ProviderCallResult chargeAndResolve(Payment payment) {
        ProviderCallResult result = providerClient.charge(payment);
        meterRegistry.counter(M_PROVIDER_CALLS,
                "outcome", result.finalOutcome().name().toLowerCase()).increment();

        transactionService.resolve(payment.getPaymentId(), result);
        return result;
    }
}
