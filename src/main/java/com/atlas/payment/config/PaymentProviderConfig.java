package com.atlas.payment.config;

import com.atlas.payment.client.PaymentProviderProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the Fake Payment Provider HTTP client. Binds {@link PaymentProviderProperties} and exposes a
 * {@link RestClient} whose connect and read timeouts are the configured per-call timeout (5s,
 * service.md §Provider Call). A read timeout surfaces as a transient TIMEOUT in the retry policy.
 */
@Configuration
@EnableConfigurationProperties(PaymentProviderProperties.class)
public class PaymentProviderConfig {

    @Bean
    public RestClient paymentProviderRestClient(PaymentProviderProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.timeout())
                .withReadTimeout(properties.timeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
