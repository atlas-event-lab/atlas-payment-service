package com.atlas.payment.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Aggregate Root for the Payment domain (services/payment/service.md §Owns).
 * Owns the payment lifecycle and the history of provider attempts/responses. A Payment belongs to
 * exactly one Booking ({@code bookingId}); its state is independent from Booking state and follows
 * services/payment/state_machine.md. State changes go through the service's transition guard.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID paymentId;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ToString.Include
    private PaymentStatus status;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(
                name = "amount",
                column = @Column(name = "amount", nullable = false, precision = 19, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false, length = 3))
    })
    private Money amount;

    /** Optional human-readable cause; set on FAILED / TIMED_OUT (payment-events.yaml). */
    @Setter
    @Column(name = "reason", length = 500)
    private String reason;

    /** Provider transaction id captured on a successful charge. */
    @Setter
    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "correlation_id", updatable = false, length = 36)
    private String correlationId;

    @Column(name = "saga_id", updatable = false, length = 36)
    private String sagaId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentAttempt> attempts = new ArrayList<>();

    public Payment(UUID paymentId, UUID bookingId, Money amount, String correlationId, String sagaId) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.amount = amount;
        this.status = PaymentStatus.CREATED;
        this.correlationId = correlationId;
        this.sagaId = sagaId;
    }

    public void addAttempt(PaymentAttempt attempt) {
        attempts.add(attempt);
        attempt.setPayment(this);
    }

    public List<PaymentAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }

    /** True once the payment has reached a terminal state (SUCCEEDED / FAILED / TIMED_OUT). */
    public boolean isTerminal() {
        return status == PaymentStatus.SUCCEEDED || status == PaymentStatus.FAILED || status == PaymentStatus.TIMED_OUT;
    }
}
