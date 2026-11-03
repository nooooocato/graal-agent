package com.graalagent.concurrency;

import com.graalagent.script.event.LockFreeEventBus;
import com.graalagent.script.event.LockFreeEventBus.Cancellable;
import com.graalagent.script.event.LockFreeEventBus.EventPriority;
import com.graalagent.test.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LockFreeEventBusTest {

    public static class TestEvent {
        public final String name;
        public TestEvent(String name) {
            this.name = name;
        }
    }

    public static class CancellableTestEvent implements Cancellable {
        private boolean cancelled = false;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    public void testPriorityOrdering() {
        LockFreeEventBus bus = new LockFreeEventBus();
        List<String> order = new ArrayList<>();

        bus.register(TestEvent.class, EventPriority.NORMAL, e -> order.add("NORMAL"));
        bus.register(TestEvent.class, EventPriority.MONITOR, e -> order.add("MONITOR"));
        bus.register(TestEvent.class, EventPriority.HIGHEST, e -> order.add("HIGHEST"));
        bus.register(TestEvent.class, EventPriority.LOWEST, e -> order.add("LOWEST"));
        bus.register(TestEvent.class, EventPriority.HIGH, e -> order.add("HIGH"));
        bus.register(TestEvent.class, EventPriority.LOW, e -> order.add("LOW"));

        boolean handled = bus.emit(new TestEvent("PriorityTest"));

        Assert.assertTrue(handled, "Emit should return true when listeners are handled");
        Assert.assertEquals(6, order.size());
        Assert.assertEquals("HIGHEST", order.get(0));
        Assert.assertEquals("HIGH", order.get(1));
        Assert.assertEquals("NORMAL", order.get(2));
        Assert.assertEquals("LOW", order.get(3));
        Assert.assertEquals("LOWEST", order.get(4));
        Assert.assertEquals("MONITOR", order.get(5));
    }

    public void testCancellationShortCircuit() {
        LockFreeEventBus bus = new LockFreeEventBus();
        List<String> executed = new ArrayList<>();

        // HIGH listener cancels the event
        bus.register(CancellableTestEvent.class, EventPriority.HIGH, e -> {
            executed.add("HIGH_CANCEL");
            e.setCancelled(true);
        });

        // NORMAL and LOW should be skipped because of cancellation
        bus.register(CancellableTestEvent.class, EventPriority.NORMAL, e -> executed.add("NORMAL"));
        bus.register(CancellableTestEvent.class, EventPriority.LOW, e -> executed.add("LOW"));

        // MONITOR always receives the event to observe the final state
        bus.register(CancellableTestEvent.class, EventPriority.MONITOR, e -> {
            if (e.isCancelled()) {
                executed.add("MONITOR_OBSERVED_CANCELLED");
            }
        });

        CancellableTestEvent event = new CancellableTestEvent();
        boolean handled = bus.emit(event);

        Assert.assertTrue(handled, "Emit should return true for handled cancellable event");
        Assert.assertTrue(event.isCancelled(), "Event should be marked cancelled");
        Assert.assertEquals(2, executed.size());
        Assert.assertEquals("HIGH_CANCEL", executed.get(0));
        Assert.assertEquals("MONITOR_OBSERVED_CANCELLED", executed.get(1));
    }

    public void testConcurrentLockFreeEmitAndRegister() throws Exception {
        LockFreeEventBus bus = new LockFreeEventBus();
        int threads = 10;
        int operations = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger totalReceived = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int op = 0; op < operations; op++) {
                        if (op % 5 == 0) {
                            bus.register(TestEvent.class, EventPriority.NORMAL, e -> totalReceived.incrementAndGet());
                        }
                        bus.emit(new TestEvent("ConcurrentEmit-" + id));
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        Assert.assertTrue(finished, "Concurrent emit and register should complete without deadlock");
        Assert.assertTrue(totalReceived.get() > 0, "Listeners should receive emitted events concurrently");
    }

    public void testExceptionIsolation() {
        LockFreeEventBus bus = new LockFreeEventBus();
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        bus.register(TestEvent.class, EventPriority.HIGHEST, e -> order.add("FIRST"));
        bus.register(TestEvent.class, EventPriority.HIGH, e -> {
            throw new RuntimeException("Deliberate listener fault");
        });
        bus.register(TestEvent.class, EventPriority.NORMAL, e -> order.add("THIRD"));

        boolean handled = bus.emit(new TestEvent("ExceptionIsolationTest"));

        Assert.assertTrue(handled, "Emit should still return true even if one listener throws");
        Assert.assertEquals(2, order.size());
        Assert.assertEquals("FIRST", order.get(0));
        Assert.assertEquals("THIRD", order.get(1));
    }
}
