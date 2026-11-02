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
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ScriptManager {
    private static volatile ScriptManager instance;

    private static final ThreadLocal<Object> currentCommandPlayer = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> commandExecutingFromConsole = new ThreadLocal<>();

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
        com.mcagent.datapack.DatapackManager.setScriptsDir(scriptsDir);
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

    public static void setCommandExecutionContext(Object player, boolean isConsole) {
        currentCommandPlayer.set(player);
        commandExecutingFromConsole.set(isConsole);
    }

    public static Object getCurrentCommandPlayer() {
        return currentCommandPlayer.get();
    }

    public static boolean isCommandExecutingFromConsole() {
        Boolean b = commandExecutingFromConsole.get();
        return b != null && b;
    }

    public static void clearCommandExecutionContext() {
        currentCommandPlayer.remove();
        commandExecutingFromConsole.remove();
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
            Logger.info("Registered custom command: /" + cleanName);
            if (lastDispatcher != null) {
                registerCommandToBrigadier(lastDispatcher, cleanName);
            }
        }
    }

    public Map<String, Value> getCustomCommands() {
        return Collections.unmodifiableMap(customCommands);
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
                .allowHostClassLookup(className -> {
                    if (com.mcagent.security.ScriptSandbox.isSandboxed()) {
                        throw new SecurityException("安全限制: 游戏内玩家禁止通过 Java.type 访问 Java 主机类 (" + className + ")");
                    }
                    return true;
                })
                .allowIO(IOAccess.ALL)
                .allowExperimentalOptions(true)
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .build();

        ScriptGlobals.setupBindings(context, agentAPI, eventBus);

        Logger.info("JavaScript Polyglot Context initialized successfully.");
    }

    public synchronized void reload() {
        Logger.info("Reloading JavaScript scripts from " + scriptsDir.getAbsolutePath() + "...");
        initContext();
        loadAllScripts();
        com.mcagent.datapack.DatapackManager.reloadServerResources();

        Object s = AgentBridge.getServer();
        if (s != null) {
            com.mcagent.server.ServerCommandDispatcher.syncCommandsToPlayers(s);
        }

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

    public synchronized Object evalSandboxed(String code, Object player) throws Exception {
        if (context == null) throw new IllegalStateException("Context not initialized");
        try {
            com.mcagent.security.ScriptSandbox.setSandboxed(true);
            setCommandExecutionContext(player, false);
            Value result = context.eval("js", code);
            if (result == null || result.isNull()) return "null";
            if (result.isHostObject()) return result.asHostObject();
            return result.toString();
        } finally {
            clearCommandExecutionContext();
            com.mcagent.security.ScriptSandbox.clear();
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

    public synchronized void onServerStart(Object server) {
        if (!gameInitialized) {
            gameInitialized = true;
            Logger.info("MinecraftServer ready. Emitting 'serverInit' and 'init' events.");
            eventBus.emit("serverInit", server);
            eventBus.emit("init", server);
        }
    }

    public synchronized void onServerTick(Object server) {
        if (AgentBridge.getMinecraft() == null) {
            if (!gameInitialized) {
                gameInitialized = true;
                Logger.info("Dedicated server tick initialized. Emitting 'serverInit' and 'init' events.");
                eventBus.emit("serverInit", server);
                eventBus.emit("init", server);
            }
            eventBus.emit("tick");
        }
        eventBus.emit("serverTick", server);
    }

    public synchronized void onServerStop() {
        eventBus.emit("serverStop");
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

        // 1. Client-only local debug commands: /cjs <expression> or /clientjs <expression>
        if (trimmed.startsWith("cjs ") || trimmed.equals("cjs") || trimmed.startsWith("clientjs ") || trimmed.equals("clientjs")) {
            String code = "";
            if (trimmed.startsWith("cjs ") || trimmed.equals("cjs")) {
                code = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
            } else if (trimmed.startsWith("clientjs ") || trimmed.equals("clientjs")) {
                code = trimmed.length() > 8 ? trimmed.substring(8).trim() : "";
            }
            if (code.isEmpty()) {
                AgentBridge.sendChatMessage("§6用法: §e/cjs <客户端JavaScript表达式>");
                return true;
            }
            executeJsCommand(code);
            return true;
        }

        // 2. Multiplayer Passthrough:
        // In multiplayer, client must NOT swallow server commands (/js, /reloadjs, custom commands).
        // Return false so ClientPacketListener forwards them to the server via ServerboundChatCommandPacket.
        if (AgentBridge.isMultiplayer()) {
            return false;
        }

        // 3. /js <expression>
        if (trimmed.startsWith("js ") || trimmed.equals("js")) {
            String code = trimmed.length() > 2 ? trimmed.substring(2).trim() : "";
            if (code.isEmpty()) {
                AgentBridge.sendChatMessage("§6用法: §e/js <JavaScript表达式>");
                return true;
            }
            executeJsCommand(code);
            return true;
        }

        // 4. /reloadjs or /jsreload
        if (trimmed.equalsIgnoreCase("reloadjs") || trimmed.equalsIgnoreCase("jsreload")) {
            reload();
            return true;
        }

        // 5. Custom commands registered via agent.registerCommand
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

        try {
            // Client always registers local debug commands /cjs and /clientjs
            registerCommandToBrigadier(dispatcher, "cjs");
            registerCommandToBrigadier(dispatcher, "clientjs");

            // In multiplayer mode, client autocompletion is driven exclusively by the server's network packet.
            // Do not inject client-local custom commands to prevent pollution / desync.
            if (AgentBridge.isMultiplayer()) {
                return;
            }

            // In singleplayer / integrated server mode, inject local custom commands
            for (String cmdName : customCommands.keySet()) {
                registerCommandToBrigadier(dispatcher, cmdName);
            }
        } catch (Throwable t) {
            Logger.error("Failed to register custom commands to client Brigadier dispatcher", t);
        }
    }

    private void registerCommandToBrigadier(Object dispatcher, String cmdName) {
        try {
            ClassLoader cl = dispatcher.getClass().getClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }

            Class<?> literalBuilderClass = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder", true, cl);
            Class<?> argumentsClass = Class.forName("com.mojang.brigadier.arguments.StringArgumentType", true, cl);
            Class<?> requiredBuilderClass = Class.forName("com.mojang.brigadier.builder.RequiredArgumentBuilder", true, cl);
            Class<?> commandClass = Class.forName("com.mojang.brigadier.Command", true, cl);
            Class<?> argumentTypeClass = Class.forName("com.mojang.brigadier.arguments.ArgumentType", true, cl);
            Class<?> argumentBuilderClass = Class.forName("com.mojang.brigadier.builder.ArgumentBuilder", true, cl);

            Method literalMethod = literalBuilderClass.getMethod("literal", String.class);
            Method greedyStringMethod = argumentsClass.getMethod("greedyString");
            Method argumentMethod = requiredBuilderClass.getMethod("argument", String.class, argumentTypeClass);
            Method thenMethod = literalBuilderClass.getMethod("then", argumentBuilderClass);
            Method executesRequired = requiredBuilderClass.getMethod("executes", commandClass);
            Method registerMethod = dispatcher.getClass().getMethod("register", literalBuilderClass);

            Object literal = literalMethod.invoke(null, cmdName);
            Object greedyArg = greedyStringMethod.invoke(null);
            Object codeArg = argumentMethod.invoke(null, "args", greedyArg);

            Object dummyCommand = java.lang.reflect.Proxy.newProxyInstance(
                    cl,
                    new Class<?>[]{commandClass},
                    (proxy, method, args) -> 1
            );

            executesRequired.invoke(codeArg, dummyCommand);
            thenMethod.invoke(literal, codeArg);
            registerMethod.invoke(dispatcher, literal);
        } catch (Throwable t) {
            Logger.error("Failed to inject /" + cmdName + " into client dispatcher", t);
        }
    }

    private void startWatcher() {
        if (watcherThread != null && watcherThread.isAlive()) {
            return;
        }

        watcherThread = new Thread(() -> {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                Path path = scriptsDir.toPath();
                path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
                Logger.info("Started hot-reload watcher on " + scriptsDir.getAbsolutePath());

                while (running.get()) {
                    WatchKey key;
                    try {
                        key = watcher.take();
                    } catch (InterruptedException e) {
                        break;
                    }

                    boolean changed = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changedPath = (Path) event.context();
                        if (changedPath.toString().endsWith(".js")) {
                            Logger.info("Detected change in " + changedPath + " (" + event.kind().name() + ")");
                            changed = true;
                        }
                    }

                    if (changed) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {}
                        reload();
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (IOException e) {
                Logger.error("File watcher stopped unexpectedly", e);
            }
        }, "mc-agent-watcher");

        watcherThread.setDaemon(true);
        watcherThread.start();
    }

    public void stop() {
        running.set(false);
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        agentAPI.shutdown();
        if (context != null) {
            try {
                context.close(true);
            } catch (Throwable ignored) {}
        }
    }
}
