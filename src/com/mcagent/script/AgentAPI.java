package com.mcagent.script;

import com.mcagent.bridge.AgentBridge;
import com.mcagent.util.Logger;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentAPI {
    private final File scriptsDir;
    private final ScriptManager scriptManager;
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
    }

    public void reload() {
        Logger.info("Reload requested via agent.reload().");
        scriptManager.reload();
    }

    public Object eval(String jsCode) {
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
