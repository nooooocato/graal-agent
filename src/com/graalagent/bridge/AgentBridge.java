package com.graalagent.bridge;

import com.graalagent.script.ScriptManager;
import com.graalagent.util.Logger;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.CompletableFuture;

public class AgentBridge {
    private static volatile Object cachedMinecraft = null;
    private static volatile Object cachedServer = null;
    private static volatile Boolean mockMultiplayer = null;

    public static void setMockMultiplayer(Boolean value) {
        mockMultiplayer = value;
    }

    public static void executeOnGameThread(Runnable task) {
        if (task == null) return;
        EnvironmentContext.getLiveContext().execute(task);
    }

    public static boolean isMultiplayer() {
        if (mockMultiplayer != null) {
            return mockMultiplayer;
        }
        Object mc = getMinecraft();
        if (mc != null) {
            try {
                MethodHandle isLocalServerMh = MethodHandleCache.findVirtualByName(mc.getClass(), "isLocalServer");
                boolean local = (boolean) isLocalServerMh.invoke(mc);
                if (!local) {
                    return true;
                }
            } catch (Throwable ignored) {}
            try {
                MethodHandle getCurrentServerMh = MethodHandleCache.findVirtualByName(mc.getClass(), "getCurrentServer");
                Object serverData = getCurrentServerMh.invoke(mc);
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
            MethodHandle getInstanceMh = MethodHandleCache.findStaticByName(mcClass, "getInstance");
            cachedMinecraft = getInstanceMh.invoke();
            if (cachedMinecraft != null) {
                EnvironmentContext.onInstanceCaptured(cachedMinecraft);
            }
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
        if (server != null) {
            EnvironmentContext.onInstanceCaptured(server);
        }
    }

    public static void onServerStart(Object server) {
        cachedServer = server;
        if (server != null) {
            EnvironmentContext.onInstanceCaptured(server);
        }
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
        com.graalagent.server.ServerCommandDispatcher.registerServerCommands(dispatcher);
    }

    public static CompletableFuture<Void> onServerReloadResources(CompletableFuture<Void> future) {
        if (future != null) {
            return future.thenApply(v -> {
                Logger.info("Vanilla reloadResources completed. Automatically triggering JS reload...");
                executeOnGameThread(() -> {
                    ScriptManager sm = ScriptManager.getInstance();
                    if (sm != null) {
                        sm.reload();
                    }
                });
                return v;
            });
        }
        return future;
    }

    public static void sendChatMessage(String message) {
        if (cachedServer != null && cachedMinecraft == null) {
            EnvironmentContext.getLiveContext().broadcastMessage(message);
            return;
        }

        Object mc = getMinecraft();
        if (mc != null) {
            Runnable task = () -> {
                try {
                    MethodHandle playerGetter = MethodHandleCache.findGetterByName(mc.getClass(), "player");
                    Object player = playerGetter.invoke(mc);
                    if (player != null) {
                        ClassLoader cl = mc.getClass().getClassLoader();
                        if (cl == null) {
                            cl = ClassLoader.getSystemClassLoader();
                        }
                        Class<?> compClass = Class.forName("net.minecraft.network.chat.Component", true, cl);
                        MethodHandle literalMh = MethodHandleCache.findStaticByName(compClass, "literal", String.class);
                        Object comp = literalMh.invoke(message);

                        MethodHandle sendSystemMessageMh = MethodHandleCache.findVirtualByName(player.getClass(), "sendSystemMessage", compClass);
                        sendSystemMessageMh.invoke(player, comp);
                        Logger.logToFileOnly("[Client UI] " + message);
                        return;
                    }
                } catch (Throwable t) {
                    Logger.error("Failed to send chat message: " + message, t);
                }
            };

            executeOnGameThread(task);
            return;
        }

        if (cachedServer != null) {
            EnvironmentContext.getLiveContext().broadcastMessage(message);
            return;
        }

        Logger.info("[Chat] " + message);
    }
}
