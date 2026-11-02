package com.graalagent.server;

import com.graalagent.script.ScriptManager;
import com.graalagent.util.Logger;
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
                    cl,
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
                    cl,
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
                for (String cmdName : sm.getCustomCommands().keySet()) {
                    Object customLiteral = literalMethod.invoke(null, cmdName);
                    requiresMethod.invoke(customLiteral, permPredicate);

                    Object customGreedyArg = greedyStringMethod.invoke(null);
                    Object customArgNode = argumentMethod.invoke(null, "args", customGreedyArg);

                    Object customCommand = Proxy.newProxyInstance(
                            cl,
                            new Class<?>[]{commandClass},
                            (proxy, method, args) -> {
                                if ("run".equals(method.getName()) && args.length == 1) {
                                    return handleCustomExecution(args[0], cmdName);
                                }
                                return 0;
                            }
                    );
                    executesLiteral.invoke(customLiteral, customCommand);
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

    public static void syncCommandsToPlayers(Object server) {
        if (server == null) {
            return;
        }

        try {
            Method getPlayerList = server.getClass().getMethod("getPlayerList");
            Object playerList = getPlayerList.invoke(server);
            if (playerList == null) {
                return;
            }

            Method getPlayers = playerList.getClass().getMethod("getPlayers");
            Object playersObj = getPlayers.invoke(playerList);
            if (!(playersObj instanceof Iterable<?> players)) {
                return;
            }

            Method getCommands = server.getClass().getMethod("getCommands");
            Object commands = getCommands.invoke(server);
            if (commands == null) {
                return;
            }

            Method sendCommandsMethod = null;
            for (Method m : commands.getClass().getMethods()) {
                if ("sendCommands".equals(m.getName()) && m.getParameterCount() == 1) {
                    sendCommandsMethod = m;
                    break;
                }
            }

            if (sendCommandsMethod == null) {
                return;
            }

            int count = 0;
            for (Object player : players) {
                try {
                    sendCommandsMethod.invoke(commands, player);
                    count++;
                } catch (Throwable t) {
                    Logger.error("Failed to sync commands to player: " + player, t);
                }
            }
            if (count > 0) {
                Logger.info("Pushed updated Brigadier command tree to " + count + " connected player(s).");
            }
        } catch (Throwable t) {
            Logger.warn("Could not push command updates to players: " + t.getMessage());
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

            ClassLoader cl = source.getClass().getClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }

            // Check permissions using Minecraft 26.3 Commands.LEVEL_ADMINS (level 2)
            Class<?> commandsClass = Class.forName("net.minecraft.commands.Commands", true, cl);
            Field levelAdminsField = commandsClass.getField("LEVEL_ADMINS");
            Object levelAdmins = levelAdminsField.get(null);

            Class<?> permSetClass = Class.forName("net.minecraft.server.permissions.PermissionSet", true, cl);
            Method checkMethod = levelAdmins.getClass().getMethod("check", permSetClass);
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
        Method getSource = null;
        Object source = null;
        try {
            getSource = context.getClass().getMethod("getSource");
            source = getSource.invoke(context);
        } catch (Throwable ignored) {}

        try {
            Method getArgument = context.getClass().getMethod("getArgument", String.class, Class.class);
            String code = (String) getArgument.invoke(context, "code", String.class);

            Object player = null;
            boolean isConsole = true;
            if (source != null) {
                try {
                    Method getPlayer = source.getClass().getMethod("getPlayer");
                    player = getPlayer.invoke(source);
                    if (player != null) {
                        isConsole = false;
                    }
                } catch (Throwable ignored) {}
            }

            ScriptManager sm = ScriptManager.getInstance();
            if (sm == null) {
                sendFeedback(source, "§c[JS 错误] ScriptManager 未初始化", false);
                return 0;
            }

            Object result;
            if (isConsole) {
                result = sm.evalInline(code);
                sendFeedback(source, "§e=> " + result, false);
                return 1;
            } else {
                result = sm.evalSandboxed(code, player);
                sendFeedback(source, "§e=> " + result, false);
                return 1;
            }
        } catch (Throwable t) {
            String errorMsg = t.getMessage() != null ? t.getMessage() : t.toString();
            if (t.getCause() != null && t.getCause().getMessage() != null) {
                errorMsg = t.getCause().getMessage();
            }
            Logger.error("Error executing server /js command: " + errorMsg, t);
            sendFeedback(source, "§c[安全限制] " + errorMsg, false);
            return 0;
        }
    }

    private static int handleReloadExecution(Object context) {
        try {
            ScriptManager sm = ScriptManager.getInstance();
            if (sm != null) {
                sm.reload();
            }
            return 1;
        } catch (Throwable t) {
            Logger.error("Error executing server /reloadjs command", t);
            return 0;
        }
    }

    private static int handleCustomExecution(Object context, String cmdName) {
        Method getSource = null;
        Object source = null;
        try {
            getSource = context.getClass().getMethod("getSource");
            source = getSource.invoke(context);
        } catch (Throwable ignored) {}

        try {
            Method getArgument = context.getClass().getMethod("getArgument", String.class, Class.class);
            String argsStr = "";
            try {
                argsStr = (String) getArgument.invoke(context, "args", String.class);
            } catch (Throwable ignored) {}

            Object player = null;
            boolean isConsole = true;
            if (source != null) {
                try {
                    Method getPlayer = source.getClass().getMethod("getPlayer");
                    player = getPlayer.invoke(source);
                    if (player != null) {
                        isConsole = false;
                    }
                } catch (Throwable ignored) {}
            }

            ScriptManager sm = ScriptManager.getInstance();
            Value callback = sm != null ? sm.getCustomCommands().get(cmdName) : null;

            if (callback != null && callback.canExecute()) {
                try {
                    ScriptManager.setCommandExecutionContext(player, isConsole);
                    callback.execute(argsStr, player, source);
                } finally {
                    ScriptManager.clearCommandExecutionContext();
                }
            } else {
                sendFeedback(source, "§c[指令错误] 未找到指令 /" + cmdName + " 的回调函数！", false);
            }
            return 1;
        } catch (Throwable t) {
            String errorMsg = t.getMessage() != null ? t.getMessage() : t.toString();
            Logger.error("指令 /" + cmdName + " 执行报错: " + errorMsg, t);
            sendFeedback(source, "§c[指令报错] /" + cmdName + ": " + errorMsg, false);
            return 0;
        }
    }

    private static void sendFeedback(Object source, String message, boolean broadcastToOps) {
        if (source == null) {
            Logger.info(message);
            return;
        }

        try {
            ClassLoader cl = source.getClass().getClassLoader();
            if (cl == null) {
                cl = ClassLoader.getSystemClassLoader();
            }
            Class<?> compClass = null;
            try {
                compClass = Class.forName("net.minecraft.network.chat.Component", true, cl);
            } catch (ClassNotFoundException ignored) {}

            Supplier<?> supplier;
            if (compClass != null) {
                Method literalMethod = compClass.getMethod("literal", String.class);
                Object comp = literalMethod.invoke(null, message);
                supplier = () -> comp;
            } else {
                supplier = () -> message;
            }

            try {
                Method sendSuccess = source.getClass().getMethod("sendSuccess", Supplier.class, boolean.class);
                sendSuccess.invoke(source, supplier, broadcastToOps);
            } catch (NoSuchMethodException e) {
                try {
                    Method sendSuccess2 = source.getClass().getMethod("sendSuccess", Object.class, boolean.class);
                    sendSuccess2.invoke(source, supplier.get(), broadcastToOps);
                } catch (NoSuchMethodException e2) {
                    Logger.info(message);
                }
            }
            Logger.logToFileOnly("[Command Feedback] " + message);
            return;
        } catch (Throwable t) {
            Logger.info(message);
        }
    }
}
