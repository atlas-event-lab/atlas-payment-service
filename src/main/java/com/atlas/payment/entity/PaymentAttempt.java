package com.atlas.payment.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single provider call attempt within {@code PROCESSING} (services/payment/service.md §Provider
 * Call). Transient failures (timeout, 503) produce additional attempts; the attempt history is
 * audit-only and never changes Payment state on its own.
 */
@Entity
@Table(name = "payment_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PaymentAttempt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    /** 1-based attempt number (1 = initial, 2..N = retries). */
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private AttemptOutcome outcome;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    /** Optional diagnostic detail (e.g. HTTP status text, exception message). */
    @Column(name = "error_detail", length = 500)
    private String errorDetail;

    @OneToOne(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PaymentProviderResponse response;

    public PaymentAttempt(
            UUID id,
            int attemptNumber,
            AttemptOutcome outcome,
            Instant startedAt,
            Instant completedAt,
            String errorDetail) {
        this.id = id;
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorDetail = errorDetail;
    }

    public void attachResponse(PaymentProviderResponse response) {
        this.response = response;
        response.setAttempt(this);
    }
}
