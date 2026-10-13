package com.lifeinbox.service;

/**
 * Functional interface for delaying execution during push delivery retry backoff.
 * Allows unit and integration tests to execute instantly without blocking threads.
 */
@FunctionalInterface
public interface PushBackoffDelayer {

    void delay(long millis) throws InterruptedException;

    PushBackoffDelayer DEFAULT = Thread::sleep;
    PushBackoffDelayer NOOP = millis -> {};
}
