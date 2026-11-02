package com.graalagent.bridge;

import com.graalagent.test.Assert;

import java.util.concurrent.atomic.AtomicBoolean;

public class DualEnvironmentTest {

    public void testClientEnvironmentDetection() {
        MockMinecraftExecutor clientMock = new MockMinecraftExecutor();
        EnvironmentContext context = new EnvironmentContext(clientMock, null);

        Assert.assertEquals(EnvironmentType.CLIENT, context.getEnvironmentType());
        Assert.assertTrue(context.isClient(), "Context should be client");
        Assert.assertFalse(context.isDedicatedServer(), "Context should not be dedicated server");

        AtomicBoolean executed = new AtomicBoolean(false);
        context.execute(() -> executed.set(true));
        Assert.assertTrue(executed.get(), "Task should be dispatched to client executor");
    }

    public void testDedicatedServerEnvironmentDetection() {
        MockMinecraftExecutor serverMock = new MockMinecraftExecutor();
        EnvironmentContext context = new EnvironmentContext(null, serverMock);

        Assert.assertEquals(EnvironmentType.DEDICATED_SERVER, context.getEnvironmentType());
        Assert.assertTrue(context.isDedicatedServer(), "Context should be dedicated server");
        Assert.assertFalse(context.isClient(), "Context should not be client");

        AtomicBoolean executed = new AtomicBoolean(false);
        context.execute(() -> executed.set(true));
        Assert.assertTrue(executed.get(), "Task should be dispatched to server executor");
    }

    public void testFallbackExecution() {
        EnvironmentContext context = new EnvironmentContext(null, null);
        Assert.assertEquals(EnvironmentType.UNKNOWN, context.getEnvironmentType());

        AtomicBoolean executed = new AtomicBoolean(false);
        context.execute(() -> executed.set(true));
        Assert.assertTrue(executed.get(), "Task should execute synchronously in fallback");
    }

    public static class MockMinecraftExecutor {
        public boolean isSameThread() {
            return false;
        }

        public void execute(Runnable runnable) {
            runnable.run();
        }
    }
}
