package com.atlas.payment.config;

import com.atlas.payment.client.PaymentProviderProperties;
import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Fake Payment Provider Feign client. Binds {@link PaymentProviderProperties} and exposes
 * the Feign {@link Request.Options} whose connect and read timeouts are the configured per-call
 * timeout (5s, service.md §Provider Call). A read timeout surfaces as a transient TIMEOUT in the
 * retry policy. The provider is the only Feign client in this service, so these options apply to it.
 */
@Configuration
@EnableConfigurationProperties(PaymentProviderProperties.class)
public class PaymentProviderConfig {

    @Bean
    public Request.Options paymentProviderRequestOptions(PaymentProviderProperties properties) {
        long timeoutMillis = properties.timeout().toMillis();
        return new Request.Options(timeoutMillis, TimeUnit.MILLISECONDS, timeoutMillis, TimeUnit.MILLISECONDS, true);
    }
}
