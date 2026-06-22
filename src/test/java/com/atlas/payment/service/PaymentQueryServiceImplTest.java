package com.atlas.payment.service;

import com.atlas.payment.dto.PaymentResponse;
import com.atlas.payment.entity.Payment;
import com.atlas.payment.entity.PaymentStatus;
import com.atlas.payment.exception.PaymentNotFoundException;
import com.atlas.payment.repository.PaymentRepository;
import com.atlas.payment.support.PaymentTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.atlas.payment.support.PaymentTestData.BOOKING_ID;
import static com.atlas.payment.support.PaymentTestData.PAYMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceImplTest {

    @Mock PaymentRepository paymentRepository;

    @InjectMocks PaymentQueryServiceImpl service;

    @Test
    void getPayment_maps_aggregate_to_contract_response() {
        Payment payment = PaymentTestData.processingPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        PaymentResponse response = service.getPayment(PAYMENT_ID);

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(response.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(response.amount().amount()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(response.amount().currency()).isEqualTo("USD");
    }

    @Test
    void getPayment_throws_when_not_found() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
