package com.atlas.payment.service;

import com.atlas.payment.dto.PaymentResponse;
import java.util.UUID;

/** Read-side queries for the Payment aggregate (payment.yaml {@code GET /payments/{paymentId}}). */
public interface PaymentQueryService {

    /**
     * Returns the payment with the given id.
     *
     * @throws com.atlas.payment.exception.PaymentNotFoundException if no such payment exists.
     */
    PaymentResponse getPayment(UUID paymentId);
}
