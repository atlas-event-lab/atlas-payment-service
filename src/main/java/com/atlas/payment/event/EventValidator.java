package com.atlas.payment.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates the structure of a consumed {@link EventEnvelope} against its Jakarta Bean Validation
 * constraints (and, via {@code @Valid}, the nested payload). A violation is a malformed message and
 * SHALL go straight to the DLQ ({@link ConstraintViolationException} is non-retryable, dlq-strategy.md).
 */
@Component
@RequiredArgsConstructor
public class EventValidator {

    private final Validator validator;

    public <T> void validate(EventEnvelope<T> envelope) {
        Set<ConstraintViolation<EventEnvelope<T>>> violations = validator.validate(envelope);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
