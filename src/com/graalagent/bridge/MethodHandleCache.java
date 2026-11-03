package com.graalagent.bridge;

import com.graalagent.util.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance MethodHandle cache pool for Minecraft 26+ core reflection calls.
 * Replaces expensive Class#getMethod and Method#invoke with cached MethodHandles.
 */
public final class MethodHandleCache {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final Map<String, MethodHandle> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean phase2WarmedUp = false;

    private MethodHandleCache() {}

    public static MethodHandle findVirtual(Class<?> clazz, String name, MethodType type) {
        String key = clazz.getName() + "#" + name + type.toMethodDescriptorString();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                return LOOKUP.findVirtual(clazz, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Failed to find virtual MethodHandle for " + k, e);
            }
        });
    }

    public static MethodHandle findStatic(Class<?> clazz, String name, MethodType type) {
        String key = clazz.getName() + "#" + name + type.toMethodDescriptorString() + "$static";
        return CACHE.computeIfAbsent(key, k -> {
            try {
                return LOOKUP.findStatic(clazz, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Failed to find static MethodHandle for " + k, e);
            }
        });
    }

    public static MethodHandle findVirtualByName(Class<?> clazz, String name, Class<?>... parameterTypes) {
        StringBuilder sb = new StringBuilder(clazz.getName()).append("#").append(name).append("(");
        for (Class<?> p : parameterTypes) {
            sb.append(p.getName()).append(";");
        }
        sb.append(")");
        String key = sb.toString();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                java.lang.reflect.Method m = clazz.getMethod(name, parameterTypes);
                return LOOKUP.unreflect(m);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Failed to unreflect virtual MethodHandle for " + k, e);
            }
        });
    }

    public static MethodHandle findStaticByName(Class<?> clazz, String name, Class<?>... parameterTypes) {
        StringBuilder sb = new StringBuilder(clazz.getName()).append("#").append(name).append("$static(");
        for (Class<?> p : parameterTypes) {
            sb.append(p.getName()).append(";");
        }
        sb.append(")");
        String key = sb.toString();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                java.lang.reflect.Method m = clazz.getMethod(name, parameterTypes);
                return LOOKUP.unreflect(m);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("Failed to unreflect static MethodHandle for " + k, e);
            }
        });
    }

    public static MethodHandle findGetterByName(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + "@" + fieldName + "$getter";
        return CACHE.computeIfAbsent(key, k -> {
            try {
                java.lang.reflect.Field f = clazz.getField(fieldName);
                return LOOKUP.unreflectGetter(f);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to unreflect getter for " + k, e);
            }
        });
    }

    public static MethodHandle get(String key) {
        return CACHE.get(key);
    }

    public static void put(String key, MethodHandle mh) {
        if (key != null && mh != null) {
            CACHE.put(key, mh);
        }
    }

    public static int size() {
        return CACHE.size();
    }

    public static void clear() {
        CACHE.clear();
        phase2WarmedUp = false;
    }

    public static boolean isPhase2WarmedUp() {
        return phase2WarmedUp;
    }

    /**
     * Phase 1 Warmup (引导期): Prewarm core JVM / standard handles on startup.
     */
    public static void warmup() {
        warmup(null);
    }

    /**
     * Phase 1 Warmup with specific ClassLoader.
     */
    public static void warmup(ClassLoader cl) {
        try {
            findStatic(String.class, "valueOf", MethodType.methodType(String.class, Object.class));
            findVirtual(Thread.class, "getName", MethodType.methodType(String.class));
        } catch (Throwable ignored) {}

        if (cl != null) {
            warmupMinecraft(cl);
        }
    }

    /**
     * Phase 2 Warmup (捕获期): Prewarm Minecraft 26+ core handles when Minecraft or Server instance is captured.
     */
    public static synchronized void warmupMinecraft(ClassLoader cl) {
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        try {
            Class<?> compClass = Class.forName("net.minecraft.network.chat.Component", false, cl);
            MethodHandle literal = LOOKUP.findStatic(compClass, "literal", MethodType.methodType(compClass, String.class));
            put("Component#literal", literal);
        } catch (Throwable t) {
            Logger.warn("MethodHandleCache: Component.literal not present on classpath yet.");
        }

        try {
            Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer", false, cl);
            MethodHandle isSameThread = LOOKUP.findVirtual(serverClass, "isSameThread", MethodType.methodType(boolean.class));
            MethodHandle execute = LOOKUP.findVirtual(serverClass, "execute", MethodType.methodType(void.class, Runnable.class));
            put("MinecraftServer#isSameThread", isSameThread);
            put("MinecraftServer#execute", execute);
        } catch (Throwable ignored) {}

        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", false, cl);
            MethodHandle isSameThread = LOOKUP.findVirtual(mcClass, "isSameThread", MethodType.methodType(boolean.class));
            MethodHandle execute = LOOKUP.findVirtual(mcClass, "execute", MethodType.methodType(void.class, Runnable.class));
            put("Minecraft#isSameThread", isSameThread);
            put("Minecraft#execute", execute);
        } catch (Throwable ignored) {}

        phase2WarmedUp = true;
    }
}
