package com.graalagent.engine;

import com.graalagent.concurrency.ConcurrencyController;
import com.graalagent.script.ScriptManager;

import java.util.concurrent.CompletableFuture;

/**
 * 脚本引擎统一服务门面 (ScriptEngineFacade)
 * 提供特权同步求值、沙箱异步求值、读写锁控制以及脚本热重载生命周期管理。
 */
public class ScriptEngineFacade {
    private static volatile ScriptEngineFacade instance;

    private final ScriptManager scriptManager;
    private final ConcurrencyController concurrencyController;

    public ScriptEngineFacade(ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
        this.concurrencyController = (scriptManager != null && scriptManager.getConcurrencyController() != null)
                ? scriptManager.getConcurrencyController()
                : new ConcurrencyController();
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

    /**
     * 特权同步执行：供控制台或核心生命周期钩子调用
     */
    public ExecutionResult evalPrivileged(String script) {
        long start = System.currentTimeMillis();
        try {
            Object res = scriptManager != null ? scriptManager.evalInline(script) : null;
            return new ExecutionResult(res, System.currentTimeMillis() - start, false, null);
        } catch (Throwable t) {
            return new ExecutionResult(null, System.currentTimeMillis() - start, false, t);
        }
    }

    /**
     * 玩家零信任沙箱异步求值
     */
    public CompletableFuture<ExecutionResult> evalSandboxedAsync(String script, Object commandSource) {
        // Phase 2 will wire this directly to AsyncWorkerPool + WatchdogTimer
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                Object res = scriptManager != null ? scriptManager.evalSandboxed(script, commandSource) : null;
                return new ExecutionResult(res, System.currentTimeMillis() - start, false, null);
            } catch (Throwable t) {
                return new ExecutionResult(null, System.currentTimeMillis() - start, false, t);
            }
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
    }
}
