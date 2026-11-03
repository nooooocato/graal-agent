package com.graalagent.server;

import com.graalagent.bridge.brigadier.BrigadierAdapter;
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
            Predicate<Object> permPredicate = ServerCommandDispatcher::checkPermission;

            // 1. Build /js <code...>
            BrigadierAdapter.literal("js")
                    .requires(permPredicate)
                    .thenGreedyWithResult("code", (source, code) -> handleJs(source, code))
                    .registerTo(dispatcher);

            // 2. Build /reloadjs and /jsreload
            for (String alias : new String[]{"reloadjs", "jsreload"}) {
                BrigadierAdapter.literal(alias)
                        .requires(permPredicate)
                        .executesWithResult((source, args) -> handleReload(source))
                        .registerTo(dispatcher);
            }

            // 3. Register custom script commands
            ScriptManager sm = ScriptManager.getInstance();
            if (sm != null) {
                for (String cmdName : sm.getCustomCommands().keySet()) {
                    BrigadierAdapter.literal(cmdName)
                            .requires(permPredicate)
                            .executesWithResult((source, args) -> handleCustom(source, cmdName, ""))
                            .thenGreedyWithResult("args", (source, args) -> handleCustom(source, cmdName, args))
                            .registerTo(dispatcher);
                }
            }

            Logger.info("ServerCommandDispatcher: Registered /js, /reloadjs, and custom commands into server CommandDispatcher.");
        } catch (Throwable t) {
            Logger.error("Failed to register server commands into Brigadier", t);
        }
    }

    public static void syncCommandsToPlayers(Object server) {
        BrigadierAdapter.syncCommandsToAll(server);
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

    public static int handleJs(Object source, String code) {
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

        try {
            Object result;
            if (isConsole) {
                result = sm.evalInline(code);
                if (result != null && String.valueOf(result).startsWith("§c[安全熔断]")) {
                    sendFeedback(source, String.valueOf(result), false);
                } else {
                    sendFeedback(source, "§e=> " + result, false);
                }
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
            if (errorMsg.startsWith("§c[安全熔断]")) {
                sendFeedback(source, errorMsg, false);
            } else {
                sendFeedback(source, "§c[安全限制] " + errorMsg, false);
            }
            return 0;
        }
    }

    public static int handleReload(Object source) {
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

    public static int handleCustom(Object source, String cmdName, String argsStr) {
        if (argsStr == null) {
            argsStr = "";
        }
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
            } catch (Throwable t) {
                String errorMsg = t.getMessage() != null ? t.getMessage() : t.toString();
                Logger.error("指令 /" + cmdName + " 执行报错: " + errorMsg, t);
                sendFeedback(source, "§c[指令报错] /" + cmdName + ": " + errorMsg, false);
                return 0;
            } finally {
                ScriptManager.clearCommandExecutionContext();
            }
        } else {
            sendFeedback(source, "§c[指令错误] 未找到指令 /" + cmdName + " 的回调函数！", false);
        }
        return 1;
    }

    public static int handleJsExecution(Object context) {
        Object source = null;
        try {
            Method getSource = context.getClass().getMethod("getSource");
            source = getSource.invoke(context);
        } catch (Throwable ignored) {}

        try {
            Method getArgument = context.getClass().getMethod("getArgument", String.class, Class.class);
            String code = (String) getArgument.invoke(context, "code", String.class);
            return handleJs(source, code);
        } catch (Throwable t) {
            return handleJs(source, "");
        }
    }

    public static int handleReloadExecution(Object context) {
        Object source = null;
        try {
            Method getSource = context.getClass().getMethod("getSource");
            source = getSource.invoke(context);
        } catch (Throwable ignored) {}
        return handleReload(source);
    }

    public static int handleCustomExecution(Object context, String cmdName) {
        Object source = null;
        try {
            Method getSource = context.getClass().getMethod("getSource");
            source = getSource.invoke(context);
        } catch (Throwable ignored) {}

        String argsStr = "";
        try {
            Method getArgument = context.getClass().getMethod("getArgument", String.class, Class.class);
            argsStr = (String) getArgument.invoke(context, "args", String.class);
        } catch (Throwable ignored) {}

        return handleCustom(source, cmdName, argsStr);
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
