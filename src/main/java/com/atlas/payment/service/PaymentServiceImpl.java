package com.atlas.payment.service;

import com.atlas.payment.client.PaymentProviderClient;
import com.atlas.payment.client.ProviderCallResult;
import com.atlas.payment.entity.Payment;
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
    private final MeterRegistry meterRegistry;

    // Charge calls actually sent to the provider, tagged by final outcome (ADR-0020,
    // Experiment 04). The service-side "no double charge" signal: this counter must track the
    // payments created, never ≈ 2×. Prometheus: atlas_payment_provider_calls_total.
    private static final String M_PROVIDER_CALLS = "atlas.payment.provider.calls";

    @Override
    public void onInventoryReserved(UUID eventId, InventoryReservedCommand command) {
        Optional<Payment> started = transactionService.beginProcessing(eventId, command);
        if (started.isEmpty()) {
            return; // duplicate event or a payment already exists for this booking
        }
        Payment payment = started.get();

        ProviderCallResult result = providerClient.charge(payment);
        meterRegistry.counter(M_PROVIDER_CALLS,
                "outcome", result.finalOutcome().name().toLowerCase()).increment();

        transactionService.resolve(payment.getPaymentId(), result);
    }
}
