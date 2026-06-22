package com.atlas.payment.client;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Abstracts the backoff wait between provider attempts so unit tests stay deterministic with no real
 * sleeps (coding-standards §Unit Tests). The default implementation sleeps the current thread.
 */
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    /** Default production implementation backed by {@link Thread#sleep(long)}. */
    @Component
    class ThreadSleeper implements Sleeper {
        @Override
        public void sleep(Duration duration) throws InterruptedException {
            long millis = duration.toMillis();
            if (millis > 0) {
                Thread.sleep(millis);
            }
        }
    }
}
