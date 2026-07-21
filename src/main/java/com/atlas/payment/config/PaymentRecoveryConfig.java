package com.atlas.payment.config;

import com.atlas.payment.scheduler.PaymentRecoveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link PaymentRecoveryProperties} ({@code atlas.payment.recovery.*}) for the
 * stale-PROCESSING recovery sweep (ADR-0021).
 */
@Configuration
@EnableConfigurationProperties(PaymentRecoveryProperties.class)
public class PaymentRecoveryConfig {}
