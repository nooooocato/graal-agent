package com.graalagent.bridge.brigadier;

import com.graalagent.test.Assert;
import com.mojang.brigadier.CommandDispatcher;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class BrigadierAdapterTest {

    public void testLiteralNodeRegistrationAndExecution() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AtomicBoolean executed = new AtomicBoolean(false);

        BrigadierAdapter.literal("ping")
                .requires(source -> true)
                .executes((source, args) -> executed.set(true))
                .registerTo(dispatcher);

        int result = dispatcher.execute("ping", new Object());
        Assert.assertEquals(1, result);
        Assert.assertTrue(executed.get(), "Literal command callback should have executed");
    }

    public void testGreedyArgumentNodeRegistrationAndExecution() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AtomicReference<String> capturedCode = new AtomicReference<>();

        BrigadierAdapter.literal("eval")
                .requires(source -> true)
                .thenGreedy("expr", (source, code) -> capturedCode.set(code))
                .registerTo(dispatcher);

        int result = dispatcher.execute("eval player.getHealth() + 10", new Object());
        Assert.assertEquals(1, result);
        Assert.assertEquals("player.getHealth() + 10", capturedCode.get());
    }

    public void testDualExecutionLiteralAndGreedy() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        AtomicReference<String> capturedArgs = new AtomicReference<>();

        BrigadierAdapter.literal("customcmd")
                .requires(source -> true)
                .executes((source, args) -> capturedArgs.set(args != null ? args : "<empty>"))
                .thenGreedy("args", (source, args) -> capturedArgs.set(args))
                .registerTo(dispatcher);

        // Test without args
        dispatcher.execute("customcmd", new Object());
        Assert.assertEquals("<empty>", capturedArgs.get());

        // Test with args
        dispatcher.execute("customcmd hello world from agent", new Object());
        Assert.assertEquals("hello world from agent", capturedArgs.get());
    }

    public void testPermissionDenied() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        BrigadierAdapter.literal("secret")
                .requires(source -> false) // Deny
                .executes((source, args) -> {})
                .registerTo(dispatcher);

        Assert.assertThrows(Exception.class, () -> {
            dispatcher.execute("secret", new Object());
        });
    }
}
