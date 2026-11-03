package com.graalagent.concurrency;

import com.graalagent.engine.ExecutionResult;
import com.graalagent.engine.ScriptEngineFacade;
import com.graalagent.test.Assert;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AsyncWorkerPoolTest {

    public void testNormalAsyncExecution() throws Exception {
        ScriptEngineFacade facade = new ScriptEngineFacade(null);
        CompletableFuture<ExecutionResult> future = facade.evalSandboxedAsync("15 + 27", null);

        ExecutionResult result = future.get(2, TimeUnit.SECONDS);
        Assert.assertNotNull(result, "Result should not be null");
        Assert.assertTrue(result.isSuccess(), "Execution should be successful");
        Assert.assertFalse(result.timedOut(), "Execution should not time out");
        Assert.assertEquals("42", String.valueOf(result.returnValue()));
        Assert.assertTrue(result.formatChatMessage().contains("=> 42"), "Formatted message should display result");
    }

    public void testDeadLoopTimeoutSafety() throws Exception {
        ScriptEngineFacade facade = new ScriptEngineFacade(null);
        CompletableFuture<ExecutionResult> future = facade.evalSandboxedAsync("while (true) {}", null, Duration.ofMillis(50));

        ExecutionResult result = future.get(3, TimeUnit.SECONDS);
        Assert.assertNotNull(result, "Result should not be null");
        Assert.assertFalse(result.isSuccess(), "Execution should not be successful");
        Assert.assertTrue(result.timedOut(), "Execution must be marked as timed out");
        Assert.assertNotNull(result.error(), "Error should be recorded");

        String chatMsg = result.formatChatMessage();
        Assert.assertTrue(chatMsg.contains("安全熔断"), "Chat message must contain security trip notice: " + chatMsg);
        Assert.assertTrue(chatMsg.contains("已被看门狗强制终止"), "Chat message must mention watchdog termination: " + chatMsg);
    }

    public void testHostAccessBlockedInSandbox() throws Exception {
        ScriptEngineFacade facade = new ScriptEngineFacade(null);
        CompletableFuture<ExecutionResult> future = facade.evalSandboxedAsync("Java.type('java.lang.System')", null, Duration.ofSeconds(2));

        ExecutionResult result = future.get(3, TimeUnit.SECONDS);
        Assert.assertNotNull(result, "Result should not be null");
        Assert.assertFalse(result.isSuccess(), "Java.type should be blocked and fail");
        Assert.assertNotNull(result.error(), "Error should be present");
        Assert.assertTrue(result.formatChatMessage().contains("脚本错误"), "Format message should report script error: " + result.formatChatMessage());
    }
}
