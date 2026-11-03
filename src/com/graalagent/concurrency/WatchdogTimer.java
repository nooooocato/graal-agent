package com.graalagent.concurrency;

import org.graalvm.polyglot.Context;

import java.time.Duration;

public final class WatchdogTimer {
    public static final Duration DEFAULT_BUDGET = Duration.ofMillis(50);

    public interface WatchdogToken {
        void cancel();
        boolean hasTriggered();
    }

    public static WatchdogToken schedule(Context context, Duration budget) {
        // Red phase stub: does not schedule any interruption
        return new WatchdogToken() {
            @Override
            public void cancel() {}

            @Override
            public boolean hasTriggered() {
                return false;
            }
        };
    }
}
