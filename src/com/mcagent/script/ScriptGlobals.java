package com.mcagent.script;

import com.mcagent.bridge.AgentBridge;
import com.mcagent.security.ScriptSandbox;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.function.Supplier;

public class ScriptGlobals {

    public static void setupBindings(Context context, AgentAPI agentAPI, EventBus eventBus) {
        Value bindings = context.getBindings("js");

        // Expose native APIs
        bindings.putMember("agent", agentAPI);
        bindings.putMember("events", eventBus);
        bindings.putMember("__getMinecraft", (Supplier<Object>) AgentBridge::getMinecraft);
        bindings.putMember("__getMinecraftServer", (Supplier<Object>) AgentBridge::getServer);
        bindings.putMember("__getCommandPlayer", (Supplier<Object>) ScriptManager::getCurrentCommandPlayer);
        bindings.putMember("__isConsoleCommand", (Supplier<Boolean>) ScriptManager::isCommandExecutingFromConsole);
        bindings.putMember("__isSandboxed", (Supplier<Boolean>) ScriptSandbox::isSandboxed);

        // Define standard environment utilities and dynamic getters
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
                    const cmdPlayer = __getCommandPlayer();
                    if (cmdPlayer) return cmdPlayer;
                    const m = __getMinecraft();
                    if (m && m.player) return m.player;
                    if (__isConsoleCommand()) {
                        throw new Error("缺少玩家实体参数 (player is null): 控制台无法执行需要玩家上下文的操作");
                    }
                    return null;
                },
                configurable: true
            });

            Object.defineProperty(globalThis, 'world', {
                get: () => {
                    const cmdPlayer = __getCommandPlayer();
                    if (cmdPlayer && cmdPlayer.level) {
                        try {
                            return typeof cmdPlayer.level === 'function' ? cmdPlayer.level() : cmdPlayer.level;
                        } catch (e) {}
                    }
                    const m = __getMinecraft();
                    if (m && m.level) return m.level;
                    const s = __getMinecraftServer();
                    if (s && s.overworld) {
                        try {
                            return typeof s.overworld === 'function' ? s.overworld() : s.overworld;
                        } catch (e) {}
                    }
                    return null;
                },
                configurable: true
            });

            Object.defineProperty(globalThis, 'level', {
                get: () => globalThis.world,
                configurable: true
            });

            Object.defineProperty(globalThis, 'server', {
                get: () => {
                    if (__isSandboxed()) {
                        throw new Error("安全权限拒绝: 游戏内玩家禁止访问 server 对象");
                    }
                    return __getMinecraftServer();
                },
                configurable: true
            });

            Object.defineProperty(globalThis, 'fs', {
                get: () => {
                    if (__isSandboxed()) {
                        throw new Error("安全权限拒绝: 游戏内玩家禁止访问服务器文件系统 (fs)");
                    }
                    return agent.fs;
                },
                configurable: true
            });

            // Sandbox guard for Java reflection
            const __origJava = globalThis.Java;
            Object.defineProperty(globalThis, 'Java', {
                get: () => {
                    if (__isSandboxed()) {
                        throw new Error("安全限制: 游戏内玩家禁止访问 Java 对象 (Java reflection is forbidden)");
                    }
                    return __origJava;
                },
                configurable: true
            });
        """);
    }
}
