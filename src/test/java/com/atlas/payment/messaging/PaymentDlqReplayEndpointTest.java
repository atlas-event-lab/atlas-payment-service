package com.atlas.payment.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class PaymentDlqReplayEndpointTest {

    private final PaymentDlqReplayer replayer = mock(PaymentDlqReplayer.class);
    private final PaymentDlqReplayEndpoint endpoint = new PaymentDlqReplayEndpoint(replayer);

    @Test
    void control_defaultsToStart_whenActionOmitted() {
        endpoint.control(null);
        verify(replayer).start();
    }

    @Test
    void control_start_delegatesToStart() {
        endpoint.control("start");
        verify(replayer).start();
    }

    @Test
    void control_stop_delegatesToStop() {
        endpoint.control("STOP"); // case-insensitive
        verify(replayer).stop();
    }

    @Test
    void control_rejectsUnknownAction() {
        assertThatThrownBy(() -> endpoint.control("drain"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start");
        verifyNoInteractions(replayer);
    }

    @Test
    void status_delegatesToReplayer() {
        endpoint.status();
        verify(replayer).status();
    }
}
