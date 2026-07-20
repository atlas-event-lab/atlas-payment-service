package com.atlas.payment.messaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Runtime control for DLQ replay (ADR-0022; dlq-strategy.md: "DLQ consumers SHALL NOT automatically
 * retry. Manual replay is allowed.").
 * <p>
 * The {@code @DltHandler} listener container is registered <b>stopped</b>
 * ({@code autoStartDltHandler="false"}). This component starts it on demand — <i>without a
 * redeploy</i> — so an operator can drain {@code inventory.reserved.dlq} deliberately. It is the
 * backing bean of the {@code /actuator/dlqreplay} endpoint.
 * <p>
 * <b>Redrive semantics (auto-stop).</b> {@link #start()} arms the DLT container with an
 * {@code idleEventInterval}; when the DLQ is fully drained the container goes quiet and Spring Kafka
 * publishes a {@link ListenerContainerIdleEvent}, on which {@link #onDltIdle} stops the container
 * automatically. So a single {@code start} drains the queue and halts itself — the operator does not
 * have to remember to stop it (an explicit {@link #stop()} remains available to abort mid-drain).
 * <p>
 * The DLT container(s) are located by the topic they consume ({@code *.dlq}) via
 * {@link KafkaListenerEndpointRegistry}, independent of Spring's generated listener ids. Start/stop
 * are idempotent.
 */
@Slf4j
@Component
public class PaymentDlqReplayer {

  private static final String DLQ_SUFFIX = ".dlq";

  private final KafkaListenerEndpointRegistry registry;
  private final long idleTimeoutMs;

  public PaymentDlqReplayer(
      KafkaListenerEndpointRegistry registry,
      @Value("${atlas.payment.dlq.idle-timeout-ms:10000}") long idleTimeoutMs) {
    this.registry = registry;
    this.idleTimeoutMs = idleTimeoutMs;
  }

  /**
   * Start the stopped DLT container(s) to drain the DLQ. Arms auto-stop (idle detection) so replay
   * halts itself once the queue is empty. Idempotent — a running container is left running.
   */
  public DlqReplayStatus start() {
    for (MessageListenerContainer container : dltContainers()) {
      container.getContainerProperties().setIdleEventInterval(idleTimeoutMs);
      if (!container.isRunning()) {
        log.warn("DLQ replay: starting DLT container id={} (auto-stop after {}ms idle)",
            container.getListenerId(), idleTimeoutMs);
        container.start();
      }
    }
    return status();
  }

  /** Explicitly stop the DLT container(s) — e.g. to abort a drain. Idempotent. */
  public DlqReplayStatus stop() {
    for (MessageListenerContainer container : dltContainers()) {
      if (container.isRunning()) {
        log.warn("DLQ replay: stopping DLT container id={}", container.getListenerId());
        container.stop();
      }
    }
    return status();
  }

  /** Whether replay is currently running, and the DLT container ids under control. */
  public DlqReplayStatus status() {
    List<String> ids = new ArrayList<>();
    boolean anyRunning = false;
    for (MessageListenerContainer container : dltContainers()) {
      ids.add(container.getListenerId());
      anyRunning = anyRunning || container.isRunning();
    }
    return new DlqReplayStatus(anyRunning, ids);
  }

  /**
   * Auto-stop: when a running DLT container has been idle for {@code idleTimeoutMs} (the DLQ is
   * drained), stop it. Uses the asynchronous {@code stop(Runnable)} because the idle event fires on
   * the consumer thread — a synchronous stop there would deadlock waiting for that same thread.
   */
  @EventListener
  public void onDltIdle(ListenerContainerIdleEvent event) {
    boolean dlqIdle = event.getTopicPartitions() != null
        && event.getTopicPartitions().stream().anyMatch(tp -> tp.topic().endsWith(DLQ_SUFFIX));
    if (!dlqIdle) {
      return;
    }
    for (MessageListenerContainer container : dltContainers()) {
      if (container.isRunning()) {
        log.warn("DLQ replay: DLQ drained (idle {}ms) — auto-stopping DLT container id={}",
            idleTimeoutMs, container.getListenerId());
        container.stop(() -> log.info("DLQ replay auto-stopped: id={}", container.getListenerId()));
      }
    }
  }

  private List<MessageListenerContainer> dltContainers() {
    List<MessageListenerContainer> result = new ArrayList<>();
    for (MessageListenerContainer container : registry.getListenerContainers()) {
      String[] topics = container.getContainerProperties().getTopics();
      if (topics != null && Arrays.stream(topics).anyMatch(topic -> topic.endsWith(DLQ_SUFFIX))) {
        result.add(container);
      }
    }
    return result;
  }
}
