package com.atlas.payment.messaging;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Actuator endpoint that drives DLQ replay on demand (ADR-0022) — the production control surface for
 * "manual replay" (dlq-strategy.md): deliberate, observable, and requiring <b>no redeploy</b>.
 * <ul>
 *   <li>{@code GET  /actuator/dlqreplay} — report whether the DLT handler is running.</li>
 *   <li>{@code POST /actuator/dlqreplay} with body {@code {"action":"start"|"stop"}} (default
 *       {@code start}) — {@code start} drains {@code inventory.reserved.dlq} (recoverable records
 *       re-driven, poison records quarantined) and <b>auto-stops when the queue is empty</b>
 *       (redrive semantics); {@code stop} is an explicit abort of an in-progress drain.</li>
 * </ul>
 * Exposed on the internal management port only (9090, not published via ingress); access is
 * network- and RBAC-gated through the Kubernetes API proxy (see ADR-0022 §Security). Start/stop are
 * idempotent.
 */
@Component
@Endpoint(id = "dlqreplay")
@RequiredArgsConstructor
public class PaymentDlqReplayEndpoint {

  private final PaymentDlqReplayer replayer;

  @ReadOperation
  public DlqReplayStatus status() {
    return replayer.status();
  }

  @WriteOperation
  public DlqReplayStatus control(@Nullable String action) {
    String normalized = action == null ? "start" : action.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "start" -> replayer.start();
      case "stop" -> replayer.stop();
      default -> throw new IllegalArgumentException("action must be 'start' or 'stop', got: " + action);
    };
  }
}
