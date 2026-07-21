package com.atlas.payment.config;

import com.atlas.payment.shared.messaging.EventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Kafka configuration.
 * KafkaTemplate and ProducerFactory are auto-configured from application.yml
 * (spring.kafka.producer.*). This class declares all payment.* topics owned by Payment Service;
 * KafkaAdmin creates them on startup if they do not exist (topics.md).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public RecordMessageConverter jsonConverter() {
        return new StringJsonMessageConverter();
    }

    /** Producer Topics */
    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentRequestedTopic() {
        return topic(EventTopics.PAYMENT_REQUESTED);
    }

    @Bean
    NewTopic paymentSucceededTopic() {
        return topic(EventTopics.PAYMENT_SUCCEEDED);
    }

    @Bean
    NewTopic paymentFailedTopic() {
        return topic(EventTopics.PAYMENT_FAILED);
    }

    @Bean
    NewTopic paymentTimedOutTopic() {
        return topic(EventTopics.PAYMENT_TIMED_OUT);
    }
}
