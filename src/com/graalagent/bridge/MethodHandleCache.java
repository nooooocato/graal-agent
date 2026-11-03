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
    }

    /**
     * Prewarm core reflection handles on startup using the system classloader.
     */
    public static void warmup() {
        warmup(null);
    }

    /**
     * Prewarm core reflection handles on startup to eliminate first-call overhead.
     */
    public static void warmup(ClassLoader cl) {
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }

        // Standard prewarmed handles
        try {
            findStatic(String.class, "valueOf", MethodType.methodType(String.class, Object.class));
            findVirtual(Thread.class, "getName", MethodType.methodType(String.class));
        } catch (Throwable ignored) {}

        // Prewarm Minecraft 26+ Component & Server handles if present on classpath
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
    }
}
