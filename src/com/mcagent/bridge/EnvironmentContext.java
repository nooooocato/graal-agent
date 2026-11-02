package com.mcagent.bridge;

import com.mcagent.util.Logger;

import java.lang.reflect.Method;

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
                Method isSameThread = target.getClass().getMethod("isSameThread");
                boolean same = (boolean) isSameThread.invoke(target);
                if (same) {
                    runnable.run();
                    return;
                }
            } catch (Throwable ignored) {}

            try {
                Method executeMethod = target.getClass().getMethod("execute", Runnable.class);
                executeMethod.invoke(target, runnable);
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
                Method literalMethod = compClass.getMethod("literal", String.class);
                Object comp = literalMethod.invoke(null, message);

                // 1. Send to server console
                Method sendSystem = serverInstance.getClass().getMethod("sendSystemMessage", compClass);
                sendSystem.invoke(serverInstance, comp);

                // 2. Broadcast to all connected players if player list is present
                try {
                    Method getPlayerList = serverInstance.getClass().getMethod("getPlayerList");
                    Object playerList = getPlayerList.invoke(serverInstance);
                    if (playerList != null) {
                        Method broadcast = playerList.getClass().getMethod("broadcastSystemMessage", compClass, boolean.class);
                        broadcast.invoke(playerList, comp, false);
                    }
                } catch (Throwable ignored) {}
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
