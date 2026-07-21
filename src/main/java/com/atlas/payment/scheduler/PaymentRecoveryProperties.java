package com.atlas.payment.scheduler;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the stale-PROCESSING payment recovery sweep (ADR-0021;
 * coding-standards §Spring Boot/§Configuration — no hardcoded values).
 *
 * @param staleAfter how long a payment may sit in PROCESSING before the sweep re-drives it.
 *                   Per the cascading timeout budget it SHALL exceed the worst-case provider
 *                   call budget (so a legitimately in-flight charge is never swept) and SHALL
 *                   stay — including one sweep + recovery pass — under Inventory's reservation
 *                   TTL (15m) and Booking's pending safety-net (30m).
 *
 * <p>The sweep interval is read directly from {@code atlas.payment.recovery.sweep-interval-ms}
 * by the scheduler's {@code @Scheduled(fixedDelayString=...)} (same approach as the Inventory
 * reservation TTL sweep).
 */
@ConfigurationProperties(prefix = "atlas.payment.recovery")
public record PaymentRecoveryProperties(Duration staleAfter) {}
