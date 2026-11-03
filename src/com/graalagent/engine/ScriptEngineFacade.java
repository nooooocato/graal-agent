package com.graalagent.engine;

import com.graalagent.concurrency.AsyncWorkerPool;
import com.graalagent.concurrency.ConcurrencyController;
import com.graalagent.concurrency.WatchdogTimer;
import com.graalagent.script.ScriptManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 脚本引擎统一服务门面 (ScriptEngineFacade)
 * 提供特权同步求值、沙箱异步求值、读写锁控制以及脚本热重载生命周期管理。
 */
public class ScriptEngineFacade {
    private static volatile ScriptEngineFacade instance;

    private final ScriptManager scriptManager;
    private final ConcurrencyController concurrencyController;
    private final AsyncWorkerPool workerPool;

    public ScriptEngineFacade(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
        this.concurrencyController = (scriptManager != null && scriptManager.getConcurrencyController() != null)
                ? scriptManager.getConcurrencyController()
                : new ConcurrencyController();
        this.workerPool = AsyncWorkerPool.getInstance();
        instance = this;
    }

    public static ScriptEngineFacade getInstance() {
        if (instance == null) {
            synchronized (ScriptEngineFacade.class) {
                if (instance == null) {
                    ScriptManager sm = ScriptManager.getInstance();
                    if (sm != null) {
                        instance = new ScriptEngineFacade(sm);
                    }
                }
            }
        }
        return instance;
    }

    public static void setInstance(ScriptEngineFacade facade) {
        instance = facade;
    }

    public ConcurrencyController getConcurrencyController() {
        return concurrencyController;
    }

    public ScriptManager getScriptManager() {
        return scriptManager;
    }

    public AsyncWorkerPool getWorkerPool() {
        return workerPool;
    }

    /**
     * 特权同步执行：供控制台或核心生命周期钩子调用
     */
    public ExecutionResult evalPrivileged(String script) {
        long start = System.currentTimeMillis();
        try {
            Object res = scriptManager != null ? scriptManager.evalInline(script) : null;
            return ExecutionResult.success(res, System.currentTimeMillis() - start);
        } catch (Throwable t) {
            return ExecutionResult.failure(t, System.currentTimeMillis() - start);
        }
    }

    /**
     * 玩家零信任沙箱异步求值：派发至 AsyncWorkerPool 并搭载 50ms 超时看门狗
     */
    public CompletableFuture<ExecutionResult> evalSandboxedAsync(String script, Object commandSource) {
        return evalSandboxedAsync(script, commandSource, WatchdogTimer.DEFAULT_BUDGET);
    }

    /**
     * 玩家零信任沙箱异步求值，支持自定义超时预算
     */
    public CompletableFuture<ExecutionResult> evalSandboxedAsync(String script, Object commandSource, Duration timeout) {
        Context sandboxContext = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .option("js.ecmascript-version", "latest")
                .build();
        try {
            sandboxContext.initialize("js");
        } catch (Throwable ignored) {}

        return workerPool.submitScript(sandboxContext, script, timeout)
                .whenComplete((res, ex) -> {
                    try {
                        sandboxContext.close(true);
                    } catch (Throwable ignored) {}
                });
    }

    /**
     * 触发全量脚本与 Modlet 热重载 (获取排他写锁)
     */
    public void reloadScripts() {
        if (scriptManager != null) {
            scriptManager.reload();
        } else {
            concurrencyController.executeExclusiveReload(() -> {});
        }
    }

    /**
     * 优雅关闭所有容器池并释放资源
     */
    public void shutdown() {
        if (scriptManager != null) {
            scriptManager.stop();
        }
        workerPool.shutdown();
        WatchdogTimer.shutdown();
    }
}
