package com.graalagent.bridge;

import com.graalagent.util.Logger;

import java.lang.invoke.MethodHandle;

public class EnvironmentContext {
    private final Object clientInstance;
    private final Object serverInstance;

    public EnvironmentContext(Object clientInstance, Object serverInstance) {
        this.clientInstance = clientInstance;
        this.serverInstance = serverInstance;
    }

    public static EnvironmentContext getLiveContext() {
        return new EnvironmentContext(AgentBridge.getMinecraft(), AgentBridge.getServer());
    }

    public static void onInstanceCaptured(Object instance) {
        // Red phase stub
    }

    public static boolean isCapturedWarmedUp() {
        return false;
    }

    public EnvironmentType getEnvironmentType() {
        if (clientInstance != null) {
            return EnvironmentType.CLIENT;
        }
        if (serverInstance != null) {
            return EnvironmentType.DEDICATED_SERVER;
        }
        return EnvironmentType.UNKNOWN;
    }

    public boolean isClient() {
        return getEnvironmentType() == EnvironmentType.CLIENT;
    }

    public boolean isDedicatedServer() {
        return getEnvironmentType() == EnvironmentType.DEDICATED_SERVER;
    }

    public boolean isServer() {
        return serverInstance != null;
    }

    public Object getClient() {
        return clientInstance;
    }

    public Object getServer() {
        return serverInstance;
    }

    public void execute(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        Object target = (isDedicatedServer() || clientInstance == null) ? serverInstance : clientInstance;
        if (target != null) {
            try {
                MethodHandle isSameThreadMh = MethodHandleCache.findVirtualByName(target.getClass(), "isSameThread");
                boolean same = (boolean) isSameThreadMh.invoke(target);
                if (same) {
                    runnable.run();
                    return;
                }
            } catch (Throwable ignored) {}

            try {
                MethodHandle executeMh = MethodHandleCache.findVirtualByName(target.getClass(), "execute", Runnable.class);
                executeMh.invoke(target, runnable);
                return;
            } catch (Throwable t) {
                Logger.error("Failed to dispatch execution on target " + target.getClass().getName(), t);
            }
        }

        // Fallback: run on caller thread
        runnable.run();
    }

    public void broadcastMessage(String message) {
        if (message == null) {
            return;
        }

        if (serverInstance != null) {
            try {
                ClassLoader cl = serverInstance.getClass().getClassLoader();
                if (cl == null) {
                    cl = ClassLoader.getSystemClassLoader();
                }
                Class<?> compClass = Class.forName("net.minecraft.network.chat.Component", true, cl);
                MethodHandle literalMh = MethodHandleCache.findStaticByName(compClass, "literal", String.class);
                Object comp = literalMh.invoke(message);

                boolean sent = false;
                try {
                    MethodHandle getPlayerListMh = MethodHandleCache.findVirtualByName(serverInstance.getClass(), "getPlayerList");
                    Object playerList = getPlayerListMh.invoke(serverInstance);
                    if (playerList != null) {
                        MethodHandle broadcastMh = MethodHandleCache.findVirtualByName(playerList.getClass(), "broadcastSystemMessage", compClass, boolean.class);
                        broadcastMh.invoke(playerList, comp, false);
                        sent = true;
                    }
                } catch (Throwable ignored) {}

                if (!sent) {
                    MethodHandle sendSystemMh = MethodHandleCache.findVirtualByName(serverInstance.getClass(), "sendSystemMessage", compClass);
                    sendSystemMh.invoke(serverInstance, comp);
                }

                Logger.logToFileOnly("[Broadcast] " + message);
                return;
            } catch (Throwable t) {
                Logger.warn("Failed to broadcast on server, falling back to chat/log: " + t.getMessage());
            }
        }

        if (clientInstance != null) {
            AgentBridge.sendChatMessage(message);
            return;
        }

        Logger.info("[Broadcast] " + message);
    }
}
