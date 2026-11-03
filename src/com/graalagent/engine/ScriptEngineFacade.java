package com.graalagent.engine;

import com.graalagent.bridge.AgentBridge;
import com.graalagent.concurrency.AsyncWorkerPool;
import com.graalagent.concurrency.ConcurrencyController;
import com.graalagent.concurrency.WatchdogTimer;
import com.graalagent.script.ScriptManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 脚本引擎统一服务门面 (ScriptEngineFacade)
 * 提供特权同步求值、沙箱异步求值、读写锁控制以及脚本热重载生命周期管理。
 */
public class ScriptEngineFacade {
    private static volatile ScriptEngineFacade instance;

    /** 共享 Polyglot Engine，跨容器共享编译字节码与 ECMAScript 内建库，使 Context 创建时间缩短至 <1ms */
    private static final Engine SHARED_ENGINE = Engine.newBuilder().build();

    static {
        // 静态预热 Polyglot Engine，预解析与求值 JS 基础表达式，避免首次求值时的冷启动编译耗时消耗 50ms 任务预算
        try (Context warmup = Context.newBuilder("js").engine(SHARED_ENGINE).build()) {
            warmup.initialize("js");
            warmup.eval("js", "1 + 1");
        } catch (Throwable ignored) {}
    }

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
     * 玩家零信任沙箱异步求值，支持自定义超时预算。
     * 1. 采用 SHARED_ENGINE (<1ms) 生成独立隔离沙箱容器，零开销实例化；
     * 2. 在独立 Worker 线程执行求值，由 WatchdogTimer 实施 50ms 硬中断熔断；
     * 3. 计算结束后安全释放沙箱容器，结果派发回游戏主线程并置于 contextLock 保护域，确保 JS 链式回调绝对线程安全。
     */
    public CompletableFuture<ExecutionResult> evalSandboxedAsync(String script, Object commandSource, Duration timeout) {
        CompletableFuture<ExecutionResult> future = new CompletableFuture<>();

        Context sandboxContext;
        try {
            sandboxContext = Context.newBuilder("js")
                    .engine(SHARED_ENGINE)
                    .allowHostAccess(HostAccess.EXPLICIT)
                    .option("js.ecmascript-version", "latest")
                    .build();
        } catch (Throwable t) {
            future.complete(ExecutionResult.failure(t, 0));
            return future;
        }

        workerPool.submitScript(sandboxContext, script, timeout)
                .whenComplete((result, ex) -> {
                    try {
                        sandboxContext.close(true);
                    } catch (Throwable ignored) {}

                    ExecutionResult finalResult = (ex != null) ? ExecutionResult.failure(ex, 0) : result;
                    AgentBridge.executeOnGameThread(() -> {
                        concurrencyController.executeRead(() -> {
                            future.complete(finalResult);
                        });
                    });
                });

        return future;
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
        SHARED_ENGINE.close();
    }
}
