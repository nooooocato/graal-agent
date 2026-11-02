package com.mcagent.server;

import com.mcagent.script.ScriptManager;
import com.mcagent.util.Logger;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ServerCommandDispatcher {

    public static void registerServerCommands(Object dispatcher) {
        if (dispatcher == null) {
            return;
        }

        try {
            Class<?> literalBuilderClass = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder");
            Class<?> argumentsClass = Class.forName("com.mojang.brigadier.arguments.StringArgumentType");
            Class<?> requiredBuilderClass = Class.forName("com.mojang.brigadier.builder.RequiredArgumentBuilder");
            Class<?> commandClass = Class.forName("com.mojang.brigadier.Command");
            Class<?> argumentTypeClass = Class.forName("com.mojang.brigadier.arguments.ArgumentType");
            Class<?> argumentBuilderClass = Class.forName("com.mojang.brigadier.builder.ArgumentBuilder");

            Method literalMethod = literalBuilderClass.getMethod("literal", String.class);
            Method greedyStringMethod = argumentsClass.getMethod("greedyString");
            Method argumentMethod = requiredBuilderClass.getMethod("argument", String.class, argumentTypeClass);
            Method thenMethod = literalBuilderClass.getMethod("then", argumentBuilderClass);
            Method requiresMethod = literalBuilderClass.getMethod("requires", Predicate.class);
            Method executesRequired = requiredBuilderClass.getMethod("executes", commandClass);
            Method executesLiteral = literalBuilderClass.getMethod("executes", commandClass);
            Method registerMethod = dispatcher.getClass().getMethod("register", literalBuilderClass);

            Predicate<Object> permPredicate = ServerCommandDispatcher::checkPermission;

            // 1. Build /js <code...>
            Object jsLiteral = literalMethod.invoke(null, "js");
            requiresMethod.invoke(jsLiteral, permPredicate);

            Object greedyArg = greedyStringMethod.invoke(null);
            Object codeArg = argumentMethod.invoke(null, "code", greedyArg);

            Object jsCommand = Proxy.newProxyInstance(
                    commandClass.getClassLoader(),
                    new Class<?>[]{commandClass},
                    (proxy, method, args) -> {
                        if ("run".equals(method.getName()) && args.length == 1) {
                            return handleJsExecution(args[0]);
                        }
                        return 0;
                    }
            );
            executesRequired.invoke(codeArg, jsCommand);
            thenMethod.invoke(jsLiteral, codeArg);
            registerMethod.invoke(dispatcher, jsLiteral);

            // 2. Build /reloadjs and /jsreload
            Object reloadCommand = Proxy.newProxyInstance(
                    commandClass.getClassLoader(),
                    new Class<?>[]{commandClass},
                    (proxy, method, args) -> {
                        if ("run".equals(method.getName()) && args.length == 1) {
                            return handleReloadExecution(args[0]);
                        }
                        return 0;
                    }
            );

            for (String alias : new String[]{"reloadjs", "jsreload"}) {
                Object reloadLiteral = literalMethod.invoke(null, alias);
                requiresMethod.invoke(reloadLiteral, permPredicate);
                executesLiteral.invoke(reloadLiteral, reloadCommand);
                registerMethod.invoke(dispatcher, reloadLiteral);
            }

            // 3. Register custom script commands
            ScriptManager sm = ScriptManager.getInstance();
            if (sm != null) {
                for (Map.Entry<String, Value> entry : sm.getCustomCommands().entrySet()) {
                    String cmdName = entry.getKey();
                    Value callback = entry.getValue();

                    Object customLiteral = literalMethod.invoke(null, cmdName);
                    requiresMethod.invoke(customLiteral, permPredicate);

                    Object customGreedyArg = greedyStringMethod.invoke(null);
                    Object customArgNode = argumentMethod.invoke(null, "args", customGreedyArg);

                    Object customCommand = Proxy.newProxyInstance(
                            commandClass.getClassLoader(),
                            new Class<?>[]{commandClass},
                            (proxy, method, args) -> {
                                if ("run".equals(method.getName()) && args.length == 1) {
                                    return handleCustomExecution(args[0], callback);
                                }
                                return 0;
                            }
                    );
                    executesRequired.invoke(customArgNode, customCommand);
                    thenMethod.invoke(customLiteral, customArgNode);
                    registerMethod.invoke(dispatcher, customLiteral);
                }
            }

            Logger.info("ServerCommandDispatcher: Registered /js, /reloadjs, and custom commands into server CommandDispatcher.");
        } catch (Throwable t) {
            Logger.error("Failed to register server commands into Brigadier", t);
        }
    }

    public static boolean checkPermission(Object source) {
        if (source == null) {
            return true; // Console or test context
        }

        try {
            Method getPlayer = source.getClass().getMethod("getPlayer");
            Object player = getPlayer.invoke(source);
            if (player == null) {
                return true; // Server console or command block
            }

            // Check permissions using Minecraft 26.3 Commands.LEVEL_ADMINS (level 2)
            Class<?> commandsClass = Class.forName("net.minecraft.commands.Commands");
            Field levelAdminsField = commandsClass.getField("LEVEL_ADMINS");
            Object levelAdmins = levelAdminsField.get(null);

            Method checkMethod = levelAdmins.getClass().getMethod("check", Class.forName("net.minecraft.server.permissions.PermissionSet"));
            Method permissionsMethod = source.getClass().getMethod("permissions");
            Object perms = permissionsMethod.invoke(source);

            return (boolean) checkMethod.invoke(levelAdmins, perms);
        } catch (Throwable ignored) {
            // Fallback: check hasPermission(int) if exists
            try {
                Method hasPerm = source.getClass().getMethod("hasPermission", int.class);
                return (boolean) hasPerm.invoke(source, 2);
            } catch (Throwable t) {
                return true;
            }
        }
    }

    private static int handleJsExecution(Object context) {
        try {
            Method getSource = context.getClass().getMethod("getSource");
            Object source = getSource.invoke(context);

            Method getArgument = context.getClass().getMethod("getArgument", String.class, Class.class);
            String code = (String) getArgument.invoke(context, "code", String.class);

            ScriptManager sm = ScriptManager.getInstance();
            Object result = sm != null ? sm.evalInline(code) : "ScriptManager not initialized";

            sendFeedback(source, "§e=> " + result, false);
            return 1;
        } catch (Throwable t) {
            Logger.error("Error executing server /js command", t);
            return 0;
        }
    }

    private static int handleReloadExecution(Object context) {
        try {
            Method getSource = context.getClass().getMethod("getSource");
            Object source = getSource.invoke(context);

            ScriptManager sm = ScriptManager.getInstance();
            if (sm != null) {
                sm.reload();
            }

            sendFeedback(source, "§a[MC-JS-Agent] 服务端脚本全部重新加载成功！", true);
            return 1;
        } catch (Throwable t) {
            Logger.error("Error executing server /reloadjs command", t);
            return 0;
        }
    }

    private static int handleCustomExecution(Object context, Value callback) {
        try {
            Method getArgument = context.getClass().getMethod("getArgument", String.class, Class.class);
            String argsStr = "";
            try {
                argsStr = (String) getArgument.invoke(context, "args", String.class);
            } catch (Throwable ignored) {}

            if (callback != null && callback.canExecute()) {
                callback.execute(argsStr);
            }
            return 1;
        } catch (Throwable t) {
            Logger.error("Error executing server custom command callback", t);
            return 0;
        }
    }

    private static void sendFeedback(Object source, String message, boolean broadcastToOps) {
        if (source == null) {
            Logger.info(message);
            return;
        }

        try {
            Class<?> compClass = Class.forName("net.minecraft.network.chat.Component");
            Method literalMethod = compClass.getMethod("literal", String.class);
            Object comp = literalMethod.invoke(null, message);

            Supplier<?> supplier = () -> comp;
            Method sendSuccess = source.getClass().getMethod("sendSuccess", Supplier.class, boolean.class);
            sendSuccess.invoke(source, supplier, broadcastToOps);
            return;
        } catch (Throwable t) {
            Logger.info(message);
        }
    }
}
