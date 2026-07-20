package com.atlas.payment.messaging;

import java.util.List;

/**
 * Snapshot of the DLQ replay control surface (ADR-0022): whether the {@code @DltHandler} container
 * is currently running, and the listener ids of the DLT container(s) it controls.
 */
public record DlqReplayStatus(boolean running, List<String> dltContainers) {}
