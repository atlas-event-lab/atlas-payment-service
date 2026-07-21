package com.atlas.payment.service;

import com.atlas.payment.dto.PaymentMapper;
import com.atlas.payment.dto.PaymentResponse;
import com.atlas.payment.exception.PaymentNotFoundException;
import com.atlas.payment.repository.PaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only query service for payments (DB-004 — local entities only, ARCH-003 — own DB only). */
@Service
@RequiredArgsConstructor
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .map(PaymentMapper::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
