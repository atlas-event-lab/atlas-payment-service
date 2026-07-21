package com.atlas.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Carries the business context extracted from an {@code InventoryReserved} envelope
 * (inventory-events.yaml {@code InventoryReservedPayload}). This is the single trigger for a
 * payment: it provides the {@code bookingId} and the {@code amount} to charge, plus the saga
 * metadata propagated onto produced events.
 */
public record InventoryReservedCommand(UUID bookingId, BigDecimal amount, String correlationId, String sagaId) {}
