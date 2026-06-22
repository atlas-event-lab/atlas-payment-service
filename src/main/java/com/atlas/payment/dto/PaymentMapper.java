package com.atlas.payment.dto;

import com.atlas.payment.entity.Money;
import com.atlas.payment.entity.Payment;

/** Maps {@link Payment} aggregates to their API representation (payment.yaml). */
public final class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentResponse toResponse(Payment payment) {
        Money amount = payment.getAmount();
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getBookingId(),
                payment.getStatus(),
                new MoneyResponse(amount.getAmount(), amount.getCurrency()));
    }
}
