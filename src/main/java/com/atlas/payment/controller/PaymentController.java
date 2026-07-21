package com.atlas.payment.controller;

import com.atlas.payment.dto.PaymentResponse;
import com.atlas.payment.service.PaymentQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only Payment API (payment.yaml; OpenAPI server {@code /api/v1}). Requires a valid Keycloak
 * JWT (SEC-002). Payments are created via events, not REST (service.md §Contracts). Holds no
 * business logic (API-003) — delegates to {@link PaymentQueryService}.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentQueryService.getPayment(paymentId));
    }
}
