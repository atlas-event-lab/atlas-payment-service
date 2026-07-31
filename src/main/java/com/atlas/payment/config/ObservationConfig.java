package com.atlas.payment.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@code @Observed} support. Spring Boot does not auto-register the aspect, so we declare it
 * here; it wraps {@code @Observed} methods (e.g. {@link com.atlas.payment.client.PaymentProviderClient#charge})
 * in an Observation, producing a span plus a timer for provider-call latency.
 */
@Configuration
public class ObservationConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
