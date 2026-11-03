package com.graalagent.bridge;

import com.graalagent.test.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgentBridgeConvergenceTest {

    public void testNoBareReflectFieldsInAgentBridge() {
        for (Field f : AgentBridge.class.getDeclaredFields()) {
            Assert.assertFalse(
                    Method.class.isAssignableFrom(f.getType()),
                    "AgentBridge must not maintain raw Method field: " + f.getName()
            );
            Assert.assertFalse(
                    Field.class.isAssignableFrom(f.getType()),
                    "AgentBridge must not maintain raw Field field: " + f.getName()
            );
        }
    }

    public void testIsMultiplayerUsingMethodHandles() {
        AgentBridge.setMockMultiplayer(null);
        // By default without client or server, isMultiplayer should be false
        boolean mp = AgentBridge.isMultiplayer();
        Assert.assertFalse(mp, "Default isMultiplayer should be false");

        // When mock multiplayer is set to true
        AgentBridge.setMockMultiplayer(true);
        Assert.assertTrue(AgentBridge.isMultiplayer(), "Mock multiplayer should be true");
        AgentBridge.setMockMultiplayer(null);
    }

    public void testDualPhaseWarmupTrigger() {
        // Stage 1 warmup
        MethodHandleCache.warmup();
        Assert.assertTrue(MethodHandleCache.size() > 0, "MethodHandleCache should contain prewarmed handles after phase 1");

        // Stage 2 warmup triggered when server or client instance captured
        MockMinecraftServer server = new MockMinecraftServer();
        EnvironmentContext.onInstanceCaptured(server);
        Assert.assertTrue(EnvironmentContext.isCapturedWarmedUp(), "EnvironmentContext should record phase 2 warmup completion");
    }

    public void testExecuteOnGameThreadFallback() {
        AtomicBoolean ran = new AtomicBoolean(false);
        AgentBridge.executeOnGameThread(() -> ran.set(true));
        Assert.assertTrue(ran.get(), "executeOnGameThread should execute task even in fallback mode");
    }

    public static class MockMinecraftServer {
        public boolean isSameThread() {
            return true;
        }

        public void execute(Runnable task) {
            task.run();
        }
    }
}
