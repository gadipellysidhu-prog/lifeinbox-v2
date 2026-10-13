package com.lifeinbox.dto;

/**
 * Result metrics for Web Push notification dispatch across user subscriptions.
 * Tracks attempted subscriptions, successful deliveries, failed deliveries,
 * transient retry attempts executed, and permanently deactivated subscriptions.
 */
public record PushDispatchResult(
        int attempted,
        int successful,
        int failed,
        int retried,
        int deactivated
) {
    /**
     * Backward-compatible constructor for 3-argument callers.
     */
    public PushDispatchResult(int attempted, int successful, int failed) {
        this(attempted, successful, failed, 0, 0);
    }

    public boolean isSuccess() {
        return successful > 0;
    }
}
