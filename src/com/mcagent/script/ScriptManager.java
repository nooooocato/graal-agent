package com.mcagent.script;

import com.mcagent.bridge.AgentBridge;
import com.mcagent.util.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ScriptManager {
    private static volatile ScriptManager instance;

    private final File scriptsDir;
    private final EventBus eventBus = new EventBus();
    private final AgentAPI agentAPI;
    private final List<Value> customTransformers = new CopyOnWriteArrayList<>();
    private final Map<String, Value> customCommands = new ConcurrentHashMap<>();
    private Context context;
    private Thread watcherThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private boolean gameInitialized = false;
    private volatile Object lastDispatcher = null;

    public ScriptManager(File scriptsDir) {
        this.scriptsDir = scriptsDir;
        this.agentAPI = new AgentAPI(scriptsDir, this);
        instance = this;
    }

    public static ScriptManager getInstance() {
        return instance;
    }

    public static synchronized void init(File scriptsDir) {
        if (instance == null) {
            ScriptManager sm = new ScriptManager(scriptsDir);
            sm.start();
        }
    }

    public void start() {
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs();
        }

        initContext();
        loadAllScripts();
        startWatcher();
    }

    public synchronized void registerCustomTransformer(Value callback) {
        if (callback != null && callback.canExecute()) {
            customTransformers.add(callback);
        }
    }

    public void registerCommand(String name, Value callback) {
        if (name != null && callback != null && callback.canExecute()) {
            String cleanName = name.startsWith("/") ? name.substring(1) : name;
            customCommands.put(cleanName.toLowerCase(), callback);
            Logger.info("Registered custom client command: /" + cleanName);
            if (lastDispatcher != null) {
                registerCommandToBrigadier(lastDispatcher, cleanName);
            }
        }
    }

    public synchronized byte[] applyCustomTransformers(String className, byte[] bytes) {
        if (customTransformers.isEmpty() || context == null) {
            return null;
        }

        byte[] current = bytes;
        for (Value custom : customTransformers) {
            try {
                Value result = custom.execute(className, current);
                if (result != null && result.hasArrayElements()) {
                    long size = result.getArraySize();
                    byte[] modified = new byte[(int) size];
                    for (int i = 0; i < size; i++) {
                        modified[i] = (byte) result.getArrayElement(i).asInt();
                    }
                    current = modified;
                }
            } catch (Throwable t) {
                Logger.error("Error in custom JS transformer for class " + className, t);
            }
        }
        return (current != bytes) ? current : null;
    }

    public synchronized void executeCallback(Value callback, Object... args) {
        if (callback != null && callback.canExecute() && context != null) {
            try {
                callback.execute(args);
            } catch (Throwable t) {
                Logger.error("Error executing callback", t);
            }
        }
    }

    private synchronized void initContext() {
        if (context != null) {
            try {
                context.close(true);
            } catch (Throwable ignored) {}
        }

        eventBus.clear();
        customTransformers.clear();
        customCommands.clear();
        agentAPI.clearTimers();

        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(c -> true)
                .allowIO(IOAccess.ALL)
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build();

        Value bindings = context.getBindings("js");

        // Expose APIs
        bindings.putMember("agent", agentAPI);
        bindings.putMember("events", eventBus);
        bindings.putMember("__getMinecraft", (Supplier<Object>) AgentBridge::getMinecraft);

        // Define standard browser/Node utilities and dynamic MC globals
        context.eval("js", """
            // Console implementation
            const console = {
                log: (...args) => agent.log(args.map(x => String(x)).join(' ')),
                info: (...args) => agent.log('[INFO] ' + args.map(x => String(x)).join(' ')),
                warn: (...args) => agent.log('[WARN] ' + args.map(x => String(x)).join(' ')),
                error: (...args) => agent.log('[ERROR] ' + args.map(x => String(x)).join(' '))
            };
            const print = (...args) => console.log(...args);

            // Timer wrappers
            const setTimeout = (fn, delay) => agent.setTimeout(fn, delay || 0);
            const clearTimeout = (id) => agent.clearTimeout(id);
            const setInterval = (fn, interval) => agent.setInterval(fn, interval || 0);
            const clearInterval = (id) => agent.clearInterval(id);

            // Dynamic getters for Minecraft singletons
            Object.defineProperty(globalThis, 'mc', {
                get: () => __getMinecraft(),
                configurable: true
            });

            Object.defineProperty(globalThis, 'player', {
                get: () => {
                    const m = __getMinecraft();
                    return m ? m.player : null;
                },
                configurable: true
            });

            Object.defineProperty(globalThis, 'world', {
                get: () => {
                    const m = __getMinecraft();
                    return m ? m.level : null;
                },
                configurable: true
            });

            Object.defineProperty(globalThis, 'level', {
                get: () => {
                    const m = __getMinecraft();
                    return m ? m.level : null;
                },
                configurable: true
            });

            Object.defineProperty(globalThis, 'fs', {
                get: () => agent.fs,
                configurable: true
            });
        """);

        Logger.info("JavaScript Polyglot Context initialized successfully.");
    }

    public synchronized void reload() {
        Logger.info("Reloading JavaScript scripts from " + scriptsDir.getAbsolutePath() + "...");
        initContext();
        loadAllScripts();
        AgentBridge.sendChatMessage("§a[MC-JS-Agent] 脚本全部重新加载成功！");
    }

    public synchronized void loadAllScripts() {
        File[] files = scriptsDir.listFiles((dir, name) -> name.endsWith(".js"));
        if (files == null || files.length == 0) {
            Logger.info("No JavaScript scripts found in " + scriptsDir.getAbsolutePath());
            return;
        }

        // Sort: init.js first, then alphabetical
        Arrays.sort(files, (a, b) -> {
            if (a.getName().equalsIgnoreCase("init.js") || a.getName().startsWith("00_")) return -1;
            if (b.getName().equalsIgnoreCase("init.js") || b.getName().startsWith("00_")) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files) {
            loadScriptFile(file);
        }
    }

    public synchronized void loadScriptFile(File file) {
        try {
            Logger.info("Executing script: " + file.getName());
            Source source = Source.newBuilder("js", file).encoding(StandardCharsets.UTF_8).build();
            context.eval(source);
        } catch (Throwable t) {
            Logger.error("Error executing script " + file.getName(), t);
            AgentBridge.sendChatMessage("§c[MC-JS-Agent] 错误 (" + file.getName() + "): §f" + t.getMessage());
        }
    }

    public synchronized Object evalInline(String code) {
        if (context == null) return null;
        try {
            Value result = context.eval("js", code);
            if (result == null || result.isNull()) return "null";
            if (result.isHostObject()) return result.asHostObject();
            return result.toString();
        } catch (Throwable t) {
            return "Error: " + t.getMessage();
        }
    }

    public synchronized void onTick() {
        if (!gameInitialized) {
            Object mc = AgentBridge.getMinecraft();
            if (mc != null) {
                gameInitialized = true;
                Logger.info("Minecraft instance ready. Emitting 'init' event.");
                eventBus.emit("init", mc);
            }
        }
        eventBus.emit("tick");
    }

    public synchronized boolean onKey(long window, int key, Object event) {
        return eventBus.emit("key", window, key, event);
    }

    public synchronized boolean onSendChat(String message) {
        if (message == null) return false;

        String trimmed = message.trim();
        // Support legacy .js prefix in chat
        if (trimmed.startsWith(".js ")) {
            String code = trimmed.substring(4).trim();
            executeJsCommand(code);
            return true;
        }

        if (trimmed.equalsIgnoreCase(".reload") || trimmed.equalsIgnoreCase(".jsreload")) {
            reload();
            return true;
        }

        // Emit chat event for custom script handlers
        return eventBus.emit("chat", message);
    }

    public synchronized boolean onSendCommand(String command) {
        if (command == null) return false;

        String trimmed = command.trim();

        // 1. /js <expression>
        if (trimmed.startsWith("js ") || trimmed.equals("js")) {
            String code = trimmed.length() > 2 ? trimmed.substring(2).trim() : "";
            if (code.isEmpty()) {
                AgentBridge.sendChatMessage("§6用法: §e/js <JavaScript表达式>");
                return true;
            }
            executeJsCommand(code);
            return true;
        }

        // 2. /reloadjs or /jsreload
        if (trimmed.equalsIgnoreCase("reloadjs") || trimmed.equalsIgnoreCase("jsreload")) {
            reload();
            return true;
        }

        // 3. Custom commands registered via agent.registerCommand
        String[] parts = trimmed.split(" ", 2);
        String cmdName = parts[0].toLowerCase();
        Value customCallback = customCommands.get(cmdName);
        if (customCallback != null) {
            String argsStr = parts.length > 1 ? parts[1] : "";
            try {
                customCallback.execute(argsStr);
            } catch (Throwable t) {
                Logger.error("Error executing custom command: /" + cmdName, t);
                AgentBridge.sendChatMessage("§c[指令错误] §f" + t.getMessage());
            }
            return true;
        }

        return false;
    }

    private void executeJsCommand(String code) {
        AgentBridge.sendChatMessage("§7> " + code);
        try {
            Object res = evalInline(code);
            AgentBridge.sendChatMessage("§e=> " + res);
        } catch (Throwable t) {
            AgentBridge.sendChatMessage("§c[JS 错误] " + t.getMessage());
        }
    }

    public void onUpdateCommands(Object dispatcher) {
        if (dispatcher == null) return;
        this.lastDispatcher = dispatcher;

        registerCommandToBrigadier(dispatcher, "js");
        registerCommandToBrigadier(dispatcher, "reloadjs");
        registerCommandToBrigadier(dispatcher, "jsreload");

        for (String customCmd : customCommands.keySet()) {
            registerCommandToBrigadier(dispatcher, customCmd);
        }

        Logger.info("Brigadier command pipeline updated with /js, /reloadjs.");
    }

    private void registerCommandToBrigadier(Object dispatcher, String cmdName) {
        try {
            Class<?> literalBuilderClass = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder");
            Class<?> argumentsClass = Class.forName("com.mojang.brigadier.arguments.StringArgumentType");
            Class<?> requiredBuilderClass = Class.forName("com.mojang.brigadier.builder.RequiredArgumentBuilder");

            Method literalMethod = literalBuilderClass.getMethod("literal", String.class);
            Method greedyStringMethod = argumentsClass.getMethod("greedyString");
            Method argumentMethod = requiredBuilderClass.getMethod("argument", String.class, Class.forName("com.mojang.brigadier.arguments.ArgumentType"));
            Method thenMethod = literalBuilderClass.getMethod("then", Class.forName("com.mojang.brigadier.builder.ArgumentBuilder"));
            Method registerMethod = dispatcher.getClass().getMethod("register", literalBuilderClass);

            Object literal = literalMethod.invoke(null, cmdName);
            Object greedyArg = greedyStringMethod.invoke(null);
            Object codeArg = argumentMethod.invoke(null, "args", greedyArg);
            thenMethod.invoke(literal, codeArg);

            registerMethod.invoke(dispatcher, literal);
        } catch (Throwable t) {
            Logger.warn("Notice: Brigadier auto-suggestion for /" + cmdName + " registration bypassed: " + t.getMessage());
        }
    }

    private void startWatcher() {
        watcherThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path path = scriptsDir.toPath();
                path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                Logger.info("Started hot-reload watcher on " + scriptsDir.getAbsolutePath());

                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watchService.take();
                    } catch (InterruptedException e) {
                        break;
                    }

                    boolean shouldReload = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = (Path) event.context();
                        if (changed.toString().endsWith(".js")) {
                            Logger.info("Detected change in " + changed + " (" + event.kind().name() + ")");
                            shouldReload = true;
                        }
                    }

                    if (shouldReload) {
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException ignored) {}
                        reload();
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (IOException e) {
                Logger.error("Script directory watcher failed", e);
            }
        }, "mc-agent-watcher");
        watcherThread.setDaemon(true);
        watcherThread.start();
    }
}
