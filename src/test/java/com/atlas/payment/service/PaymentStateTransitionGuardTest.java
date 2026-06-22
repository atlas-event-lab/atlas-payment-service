package com.atlas.payment.service;

import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.exception.InvalidPaymentStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateTransitionGuardTest {

    @Test
    void allows_valid_transitions() {
        assertThatCode(() -> {
            PaymentStateTransitionGuard.assertAllowed(PaymentStatus.CREATED, PaymentStatus.PROCESSING);
            PaymentStateTransitionGuard.assertAllowed(PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED);
            PaymentStateTransitionGuard.assertAllowed(PaymentStatus.PROCESSING, PaymentStatus.FAILED);
            PaymentStateTransitionGuard.assertAllowed(PaymentStatus.PROCESSING, PaymentStatus.TIMED_OUT);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejects_skipping_processing() {
        assertThatThrownBy(() ->
                PaymentStateTransitionGuard.assertAllowed(PaymentStatus.CREATED, PaymentStatus.SUCCEEDED))
                .isInstanceOf(InvalidPaymentStateTransitionException.class);
    }

    @Test
    void rejects_transitions_out_of_terminal_states() {
        assertThatThrownBy(() ->
                PaymentStateTransitionGuard.assertAllowed(PaymentStatus.SUCCEEDED, PaymentStatus.PROCESSING))
                .isInstanceOf(InvalidPaymentStateTransitionException.class);
        assertThatThrownBy(() ->
                PaymentStateTransitionGuard.assertAllowed(PaymentStatus.FAILED, PaymentStatus.PROCESSING))
                .isInstanceOf(InvalidPaymentStateTransitionException.class);
        assertThatThrownBy(() ->
                PaymentStateTransitionGuard.assertAllowed(PaymentStatus.TIMED_OUT, PaymentStatus.SUCCEEDED))
                .isInstanceOf(InvalidPaymentStateTransitionException.class);
    }
}
