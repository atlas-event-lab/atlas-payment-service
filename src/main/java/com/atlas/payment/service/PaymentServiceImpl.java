package com.atlas.payment.service;

import com.atlas.payment.client.PaymentProviderClient;
import com.atlas.payment.client.ProviderCallResult;
import com.atlas.payment.entity.Payment;
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

    @Override
    public void onInventoryReserved(UUID eventId, InventoryReservedCommand command) {
        Optional<Payment> started = transactionService.beginProcessing(eventId, command);
        if (started.isEmpty()) {
            return; // duplicate event or a payment already exists for this booking
        }
        Payment payment = started.get();

        ProviderCallResult result = providerClient.charge(payment);

        transactionService.resolve(payment.getPaymentId(), result);
    }
}
