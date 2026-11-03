package com.graalagent.script;

import com.graalagent.bridge.AgentBridge;
import com.graalagent.script.fs.ScriptFileSystem;
import com.graalagent.util.Logger;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentAPI {
    private final File scriptsDir;
    private final ScriptManager scriptManager;
    private final ScriptFileSystem fs;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "graal-agent-timer");
        t.setDaemon(true);
        return t;
    });

    private final AtomicInteger timerIdGen = new AtomicInteger(1);
    private final Map<Integer, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    public AgentAPI(File scriptsDir, ScriptManager scriptManager) {
        this.scriptsDir = scriptsDir;
        this.scriptManager = scriptManager;
        this.fs = new ScriptFileSystem(scriptsDir);
    }

    public ScriptFileSystem getFs() {
        if (com.graalagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止访问服务器文件系统 (agent.fs)");
        }
        return fs;
    }

    public void reload() {
        if (com.graalagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止执行系统热重载 (agent.reload)");
        }
        Logger.info("Reload requested via agent.reload().");
        scriptManager.reload();
    }

    public Object eval(String jsCode) {
        if (com.graalagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止调用 agent.eval");
        }
        return scriptManager.evalInline(jsCode);
    }

    public void chat(String message) {
        AgentBridge.sendChatMessage(message);
    }

    public void log(String message) {
        Logger.info(message);
    }

    public String getScriptsDir() {
        return scriptsDir.getAbsolutePath();
    }

    public String createDatapack(String id, String description) {
        if (com.graalagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止创建数据包 (agent.createDatapack)");
        }
        try {
            java.nio.file.Path p = com.graalagent.datapack.DatapackManager.createSyntheticPack(scriptsDir, id, description, 61);
            return p.toString();
        } catch (Throwable t) {
            Logger.error("Failed to create datapack: " + id, t);
            return null;
        }
    }

    public boolean isClient() {
        return com.graalagent.bridge.EnvironmentContext.getLiveContext().isClient();
    }

    public boolean isDedicatedServer() {
        return com.graalagent.bridge.EnvironmentContext.getLiveContext().isDedicatedServer();
    }

    public Object getServer() {
        return AgentBridge.getServer();
    }

    public void defer(Value action) {
        if (action != null && action.canExecute()) {
            com.graalagent.concurrency.TransactionBuffer.enqueue(serverCtx -> {
                try {
                    if (action.canExecute()) {
                        action.execute(serverCtx);
                    }
                } catch (Throwable t) {
                    Logger.error("Error executing deferred transaction action", t);
                }
            });
        }
    }

    public void runAsync(Value callback) {
        if (callback != null && callback.canExecute()) {
            com.graalagent.concurrency.AsyncWorkerPool.getInstance().getExecutor().submit(() -> {
                scriptManager.getConcurrencyController().executeRead(() -> {
                    try {
                        if (callback.canExecute()) {
                            callback.execute();
                        }
                    } catch (Throwable t) {
                        Logger.error("Error executing runAsync callback", t);
                    }
                });
            });
        }
    }

    public void execute(Value callback) {
        if (callback != null && callback.canExecute()) {
            com.graalagent.bridge.EnvironmentContext.getLiveContext().execute(() -> {
                scriptManager.executeCallback(callback);
            });
        }
    }

    // =========================================================================
    // 定时器机制说明与已知架构问题记录 (Timer Concurrency Notice)
    // -------------------------------------------------------------------------
    // [Bug 原因 / Root Cause]:
    // GraalJS 的单 Context 默认禁止跨线程并发访问（或并发未经过 context.enter()/leave() 同步调度）。
    // 当前 setTimeout 与 setInterval 使用后台专属调度线程池 (graal-agent-timer) 直接执行 JS 回调 (scriptManager.executeCallback(callback))。
    // 当服务器主线程 (Server thread) 恰好也在执行 JS 脚本（或正在处理 tick/指令）时，
    // 后台定时器线程触发回调会抛出 IllegalStateException:
    // "Multi threaded access requested by thread graal-agent-timer but is not allowed for language(s) js"
    //
    // [解决方案规划 / Architecture Roadmap]:
    // 经讨论不采用粗暴的局部包装临时方案，而是在后续重构计划
    // 《第三阶段：核心并发、看门狗防卡死与事务缓冲 (Concurrency, Watchdog & Transaction Buffer)》
    // 中统一进行架构翻新：通过将异步/定时回调统一安全提交至 Minecraft 主线程 Tick 任务队列 (EnvironmentContext.execute)
    // 或基于双缓冲/上下文安全调度租约执行，彻底保证 GraalJS 上下文的单线程并发安全性。
    // =========================================================================
    public int setTimeout(Value callback, long delayMs) {
        int id = timerIdGen.getAndIncrement();
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                activeTimers.remove(id);
                scriptManager.executeCallback(callback);
            } catch (Throwable t) {
                Logger.error("Error in setTimeout callback (id=" + id + ")", t);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        activeTimers.put(id, future);
        return id;
    }

    public int setInterval(Value callback, long intervalMs) {
        int id = timerIdGen.getAndIncrement();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                scriptManager.executeCallback(callback);
            } catch (Throwable t) {
                Logger.error("Error in setInterval callback (id=" + id + ")", t);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        activeTimers.put(id, future);
        return id;
    }

    public void clearTimeout(int id) {
        ScheduledFuture<?> f = activeTimers.remove(id);
        if (f != null) {
            f.cancel(false);
        }
    }

    public void clearInterval(int id) {
        clearTimeout(id);
    }

    public void registerTransformer(Value transformerCallback) {
        if (transformerCallback != null && transformerCallback.canExecute()) {
            scriptManager.registerCustomTransformer(transformerCallback);
            Logger.info("Registered custom JS bytecode transformer.");
        }
    }

    public void registerCommand(String name, Value commandCallback) {
        if (name != null && commandCallback != null && commandCallback.canExecute()) {
            scriptManager.registerCommand(name, commandCallback);
        }
    }

    public void clearTimers() {
        for (ScheduledFuture<?> future : activeTimers.values()) {
            future.cancel(false);
        }
        activeTimers.clear();
    }

    public void shutdown() {
        clearTimers();
        scheduler.shutdownNow();
    }
}
