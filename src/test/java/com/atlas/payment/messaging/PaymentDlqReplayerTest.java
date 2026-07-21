package com.atlas.payment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListenerContainer;

class PaymentDlqReplayerTest {

    private final KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
    private final MessageListenerContainer dlt = mock(MessageListenerContainer.class);
    private final MessageListenerContainer main = mock(MessageListenerContainer.class);
    private final PaymentDlqReplayer replayer = new PaymentDlqReplayer(registry, 10_000L);

    @BeforeEach
    void setup() {
        when(dlt.getContainerProperties()).thenReturn(new ContainerProperties("inventory.reserved-payment.dlq"));
        when(dlt.getListenerId()).thenReturn("payment-dlt");
        when(main.getContainerProperties()).thenReturn(new ContainerProperties("inventory.reserved"));
        when(main.getListenerId()).thenReturn("payment-main");
        when(registry.getListenerContainers()).thenReturn(List.of(main, dlt));
    }

    @Test
    void start_startsOnlyTheDltContainer() {
        when(dlt.isRunning()).thenReturn(false);

        replayer.start();

        verify(dlt).start();
        verify(main, never()).start();
    }

    @Test
    void start_isIdempotent_whenDltAlreadyRunning() {
        when(dlt.isRunning()).thenReturn(true);

        replayer.start();

        verify(dlt, never()).start();
    }

    @Test
    void stop_stopsOnlyTheRunningDltContainer() {
        when(dlt.isRunning()).thenReturn(true);

        replayer.stop();

        verify(dlt).stop();
        verify(main, never()).stop();
    }

    @Test
    void status_reportsRunningAndTheDltContainerIds() {
        when(dlt.isRunning()).thenReturn(true);

        DlqReplayStatus status = replayer.status();

        assertThat(status.running()).isTrue();
        assertThat(status.dltContainers()).containsExactly("payment-dlt");
    }

    @Test
    void onDltIdle_autoStopsTheRunningDltContainer_whenDlqDrained() {
        when(dlt.isRunning()).thenReturn(true);
        ListenerContainerIdleEvent event = mock(ListenerContainerIdleEvent.class);
        when(event.getTopicPartitions()).thenReturn(List.of(new TopicPartition("inventory.reserved-payment.dlq", 0)));

        replayer.onDltIdle(event);

        verify(dlt).stop(any(Runnable.class)); // async stop (idle fires on the consumer thread)
        verify(main, never()).stop(any(Runnable.class));
    }

    @Test
    void onDltIdle_ignoresIdleOnNonDlqContainers() {
        when(main.isRunning()).thenReturn(true);
        ListenerContainerIdleEvent event = mock(ListenerContainerIdleEvent.class);
        when(event.getTopicPartitions()).thenReturn(List.of(new TopicPartition("inventory.reserved", 0)));

        replayer.onDltIdle(event);

        verify(dlt, never()).stop(any(Runnable.class));
        verify(main, never()).stop(any(Runnable.class));
    }
}
