package com.graalagent.concurrency;

import com.graalagent.test.Assert;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class WatchdogTimerTest {

    public void testDeadLoopInterruptedByWatchdog() throws Exception {
        Context context = Context.newBuilder("js").build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean cancelledCaught = new AtomicBoolean(false);
        AtomicReference<Throwable> caughtError = new AtomicReference<>();

        long startTime = System.currentTimeMillis();
        WatchdogTimer.WatchdogToken token = WatchdogTimer.schedule(context, Duration.ofMillis(50));

        Thread evalThread = new Thread(() -> {
            try {
                started.countDown();
                context.eval("js", "while (true) {}");
            } catch (PolyglotException e) {
                if (e.isCancelled()) {
                    cancelledCaught.set(true);
                }
                caughtError.set(e);
            } catch (Throwable t) {
                caughtError.set(t);
            } finally {
                finished.countDown();
            }
        }, "test-deadloop-worker");
        evalThread.setDaemon(true);
        evalThread.start();

        // Must finish within 500ms due to 50ms watchdog
        boolean didFinish = finished.await(300, TimeUnit.MILLISECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        // Cleanup in case watchdog didn't fire (to not leak thread)
        if (!didFinish) {
            context.close(true);
            evalThread.join(500);
        }

        Assert.assertTrue(didFinish, "Dead loop should have been interrupted by watchdog within 300ms (elapsed: " + elapsed + "ms)");
        Assert.assertTrue(cancelledCaught.get(), "PolyglotException.isCancelled() should be true");
        Assert.assertTrue(token.hasTriggered(), "WatchdogToken should report triggered");
    }

    public void testNormalExecutionCancelsWatchdog() {
        Context context = Context.newBuilder("js").build();
        WatchdogTimer.WatchdogToken token = WatchdogTimer.schedule(context, Duration.ofMillis(50));

        Object res = context.eval("js", "20 + 22").asInt();
        token.cancel();

        Assert.assertEquals(42, res);
        Assert.assertFalse(token.hasTriggered(), "Token should not have triggered for normal execution");
        context.close(true);
    }
}
