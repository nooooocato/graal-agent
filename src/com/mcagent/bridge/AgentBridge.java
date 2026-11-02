package com.mcagent.bridge;

import com.mcagent.script.ScriptManager;
import com.mcagent.util.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AgentBridge {
    private static volatile Object cachedMinecraft = null;
    private static volatile Object cachedServer = null;
    private static volatile Method literalMethod = null;
    private static volatile Method sendSystemMessageMethod = null;
    private static volatile Field playerField = null;
    private static volatile Boolean mockMultiplayer = null;

    public static void setMockMultiplayer(Boolean value) {
        mockMultiplayer = value;
    }

    public static boolean isMultiplayer() {
        if (mockMultiplayer != null) {
            return mockMultiplayer;
        }
        Object mc = getMinecraft();
        if (mc != null) {
            try {
                Method isLocalServer = mc.getClass().getMethod("isLocalServer");
                boolean local = (boolean) isLocalServer.invoke(mc);
                if (!local) {
                    return true;
                }
            } catch (Throwable ignored) {}
            try {
                Method getCurrentServer = mc.getClass().getMethod("getCurrentServer");
                Object serverData = getCurrentServer.invoke(mc);
                if (serverData != null) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static Object getMinecraft() {
        if (cachedMinecraft != null) {
            return cachedMinecraft;
        }
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = mcClass.getMethod("getInstance");
            cachedMinecraft = getInstance.invoke(null);
            return cachedMinecraft;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object getServer() {
        return cachedServer;
    }

    public static void setServer(Object server) {
        cachedServer = server;
    }

    public static void onServerStart(Object server) {
        cachedServer = server;
        Logger.info("MinecraftServer instance captured: " + server.getClass().getName());
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            sm.onServerStart(server);
        }
    }

    public static void onServerTick(Object server) {
        cachedServer = server;
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            sm.onServerTick(server);
        }
    }

    public static void onServerStop() {
        Logger.info("MinecraftServer stopping...");
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            sm.onServerStop();
        }
        cachedServer = null;
    }

    public static void onTick() {
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            sm.onTick();
        }
    }

    public static boolean onKey(long window, int key, Object event) {
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            return sm.onKey(window, key, event);
        }
        return false;
    }

    public static boolean onSendChat(String message) {
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            return sm.onSendChat(message);
        }
        return false;
    }

    public static boolean onSendCommand(String command) {
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            return sm.onSendCommand(command);
        }
        return false;
    }

    public static void onUpdateCommands(Object dispatcher) {
        ScriptManager sm = ScriptManager.getInstance();
        if (sm != null) {
            sm.onUpdateCommands(dispatcher);
        }
    }

    public static void onRegisterServerCommands(Object dispatcher) {
        if (dispatcher == null) {
            return;
        }
        com.mcagent.server.ServerCommandDispatcher.registerServerCommands(dispatcher);
    }

    public static java.util.concurrent.CompletableFuture<Void> onServerReloadResources(java.util.concurrent.CompletableFuture<Void> future) {
        if (future != null) {
            return future.thenApply(v -> {
                Logger.info("Vanilla reloadResources completed. Automatically triggering JS reload...");
                Object server = getServer();
                Runnable reloadTask = () -> {
                    ScriptManager sm = ScriptManager.getInstance();
                    if (sm != null) {
                        sm.reload();
                    }
                };

                if (server != null) {
                    try {
                        Method execute = server.getClass().getMethod("execute", Runnable.class);
                        execute.invoke(server, reloadTask);
                        return v;
                    } catch (Throwable ignored) {}
                }
                reloadTask.run();
                return v;
            });
        }
        return future;
    }

    public static void sendChatMessage(String message) {
        // Dedicated Server mode: broadcast directly to console and players
        if (cachedServer != null && cachedMinecraft == null) {
            EnvironmentContext.getLiveContext().broadcastMessage(message);
            return;
        }

        Object mc = getMinecraft();
        if (mc != null) {
            Runnable task = () -> {
                try {
                    if (playerField == null) {
                        playerField = mc.getClass().getField("player");
                    }
                    Object player = playerField.get(mc);
                    if (player != null) {
                        ClassLoader cl = mc.getClass().getClassLoader();
                        if (cl == null) {
                            cl = ClassLoader.getSystemClassLoader();
                        }
                        Class<?> compClass = Class.forName("net.minecraft.network.chat.Component", true, cl);
                        if (literalMethod == null) {
                            literalMethod = compClass.getMethod("literal", String.class);
                        }
                        Object comp = literalMethod.invoke(null, message);

                        if (sendSystemMessageMethod == null) {
                            sendSystemMessageMethod = player.getClass().getMethod("sendSystemMessage", compClass);
                        }
                        sendSystemMessageMethod.invoke(player, comp);
                        Logger.logToFileOnly("[Client UI] " + message);
                        return;
                    }
                } catch (Throwable t) {
                    Logger.error("Failed to send chat message: " + message, t);
                }
            };

            try {
                Method isSameThread = mc.getClass().getMethod("isSameThread");
                boolean same = (boolean) isSameThread.invoke(mc);
                if (same) {
                    task.run();
                } else {
                    Method execute = mc.getClass().getMethod("execute", Runnable.class);
                    execute.invoke(mc, task);
                }
                return;
            } catch (Throwable t) {
                task.run();
                return;
            }
        }

        if (cachedServer != null) {
            EnvironmentContext.getLiveContext().broadcastMessage(message);
            return;
        }

        Logger.info("[Chat] " + message);
    }
}
