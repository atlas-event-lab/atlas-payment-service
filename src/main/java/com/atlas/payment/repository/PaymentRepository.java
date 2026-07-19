package com.atlas.payment.repository;

import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link Payment} aggregate. Accesses only local entities (DB-004);
 * Payment never reads the Booking or Inventory databases (ARCH-003).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    /** Oldest-first batch of stale payments for the recovery sweep (ADR-0021). */
    List<Payment> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            PaymentStatus status, Instant updatedBefore);
}
