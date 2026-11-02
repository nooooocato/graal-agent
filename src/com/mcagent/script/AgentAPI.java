package com.mcagent.script;

import com.mcagent.bridge.AgentBridge;
import com.mcagent.script.fs.ScriptFileSystem;
import com.mcagent.util.Logger;
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
        Thread t = new Thread(r, "mc-agent-timer");
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
        if (com.mcagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止访问服务器文件系统 (agent.fs)");
        }
        return fs;
    }

    public void reload() {
        if (com.mcagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止执行系统热重载 (agent.reload)");
        }
        Logger.info("Reload requested via agent.reload().");
        scriptManager.reload();
    }

    public Object eval(String jsCode) {
        if (com.mcagent.security.ScriptSandbox.isSandboxed()) {
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
        if (com.mcagent.security.ScriptSandbox.isSandboxed()) {
            throw new SecurityException("安全权限拒绝: 游戏内玩家禁止创建数据包 (agent.createDatapack)");
        }
        try {
            java.nio.file.Path p = com.mcagent.datapack.DatapackManager.createSyntheticPack(scriptsDir, id, description, 61);
            return p.toString();
        } catch (Throwable t) {
            Logger.error("Failed to create datapack: " + id, t);
            return null;
        }
    }

    public boolean isClient() {
        return com.mcagent.bridge.EnvironmentContext.getLiveContext().isClient();
    }

    public boolean isDedicatedServer() {
        return com.mcagent.bridge.EnvironmentContext.getLiveContext().isDedicatedServer();
    }

    public Object getServer() {
        return AgentBridge.getServer();
    }

    public void execute(Value callback) {
        if (callback != null && callback.canExecute()) {
            com.mcagent.bridge.EnvironmentContext.getLiveContext().execute(() -> {
                scriptManager.executeCallback(callback);
            });
        }
    }

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
