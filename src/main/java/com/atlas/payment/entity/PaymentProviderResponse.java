package com.atlas.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The provider's response captured for a {@link PaymentAttempt} (services/payment/service.md
 * §Fake Payment Provider). Present when the provider returned a response within the timeout;
 * absent for a TIMEOUT attempt. Audit-only.
 */
@Entity
@Table(name = "payment_provider_responses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PaymentProviderResponse {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false, unique = true)
    private PaymentAttempt attempt;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    /** Provider business status (e.g. APPROVED / DECLINED). */
    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public PaymentProviderResponse(UUID id, int httpStatus, String providerStatus,
                                   String transactionId, String reason, Instant receivedAt) {
        this.id = id;
        this.httpStatus = httpStatus;
        this.providerStatus = providerStatus;
        this.transactionId = transactionId;
        this.reason = reason;
        this.receivedAt = receivedAt;
    }
}
