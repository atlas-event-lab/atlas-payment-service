package com.atlas.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a {@link Clock} bean so time-dependent logic (timestamps, deadlines) is deterministic
 * and testable (coding-standards §Unit Tests — "Tests SHALL be deterministic. No sleeps").
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
