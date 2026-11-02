package com.mcagent.bridge;

import com.mcagent.script.ScriptManager;
import com.mcagent.util.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AgentBridge {
    private static volatile Object cachedMinecraft = null;
    private static volatile Method literalMethod = null;
    private static volatile Method sendSystemMessageMethod = null;
    private static volatile Field playerField = null;

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

    public static void sendChatMessage(String message) {
        Object mc = getMinecraft();
        if (mc != null) {
            Runnable task = () -> {
                try {
                    if (playerField == null) {
                        playerField = mc.getClass().getField("player");
                    }
                    Object player = playerField.get(mc);
                    if (player != null) {
                        Class<?> compClass = Class.forName("net.minecraft.network.chat.Component");
                        if (literalMethod == null) {
                            literalMethod = compClass.getMethod("literal", String.class);
                        }
                        Object comp = literalMethod.invoke(null, message);

                        if (sendSystemMessageMethod == null) {
                            sendSystemMessageMethod = player.getClass().getMethod("sendSystemMessage", compClass);
                        }
                        sendSystemMessageMethod.invoke(player, comp);
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

        Logger.info("[Chat] " + message);
    }
}
