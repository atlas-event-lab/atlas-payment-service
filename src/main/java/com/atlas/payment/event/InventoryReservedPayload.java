package com.atlas.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Consumed payload of {@code InventoryReserved} (inventory-events.yaml InventoryReservedPayload).
 * Payment is the single consumer that only needs {@code bookingId} (who to charge) and {@code total}
 * (how much); the publisher also carries {@code items[]}, which Payment ignores — hence
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryReservedPayload(@NotNull UUID bookingId, @NotNull BigDecimal total) {}
