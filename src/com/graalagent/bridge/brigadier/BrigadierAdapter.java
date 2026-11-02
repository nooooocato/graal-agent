package com.graalagent.bridge.brigadier;

import com.graalagent.bridge.MethodHandleCache;
import com.graalagent.util.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Unified Brigadier command registration gateway for Minecraft 26+ (Client & Server).
 * Provides a fluent DSL to build literal and argument nodes and registers them seamlessly.
 */
public class BrigadierAdapter {

    public static NodeBuilder literal(String name) {
        return new NodeBuilder(name);
    }

    public static class NodeBuilder {
        private final String name;
        private Predicate<Object> permissionPredicate;
        private BiConsumer<Object, String> executionCallback;
        private BiConsumer<Object, String> greedyCallback;
        private String greedyArgName = "args";

        public NodeBuilder(String name) {
            this.name = name;
        }

        public NodeBuilder requires(Predicate<Object> predicate) {
            this.permissionPredicate = predicate;
            return this;
        }

        public NodeBuilder thenGreedy(String argName, BiConsumer<Object, String> callback) {
            this.greedyArgName = argName;
            this.greedyCallback = callback;
            return this;
        }

        public NodeBuilder executes(BiConsumer<Object, String> callback) {
            this.executionCallback = callback;
            return this;
        }

        public void registerTo(Object dispatcher) {
            if (dispatcher == null) return;
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

                Object literalBuilder = literalMethod.invoke(null, name);
                if (permissionPredicate != null) {
                    requiresMethod.invoke(literalBuilder, permissionPredicate);
                }

                if (executionCallback != null) {
                    Object literalCmdProxy = Proxy.newProxyInstance(
                            cl,
                            new Class<?>[]{commandClass},
                            (proxy, method, args) -> {
                                if ("run".equals(method.getName()) && args.length == 1) {
                                    Object ctx = args[0];
                                    Method getSource = ctx.getClass().getMethod("getSource");
                                    Object source = getSource.invoke(ctx);
                                    executionCallback.accept(source, null);
                                    return 1;
                                }
                                return 0;
                            }
                    );
                    executesLiteral.invoke(literalBuilder, literalCmdProxy);
                }

                if (greedyCallback != null) {
                    Object greedyArg = greedyStringMethod.invoke(null);
                    Object argNode = argumentMethod.invoke(null, greedyArgName, greedyArg);
                    if (permissionPredicate != null) {
                        try {
                            Method reqArgMethod = requiredBuilderClass.getMethod("requires", Predicate.class);
                            reqArgMethod.invoke(argNode, permissionPredicate);
                        } catch (Throwable ignored) {}
                    }

                    Object greedyCmdProxy = Proxy.newProxyInstance(
                            cl,
                            new Class<?>[]{commandClass},
                            (proxy, method, args) -> {
                                if ("run".equals(method.getName()) && args.length == 1) {
                                    Object ctx = args[0];
                                    Method getSource = ctx.getClass().getMethod("getSource");
                                    Object source = getSource.invoke(ctx);
                                    Method getArgument = ctx.getClass().getMethod("getArgument", String.class, Class.class);
                                    String argVal = (String) getArgument.invoke(ctx, greedyArgName, String.class);
                                    greedyCallback.accept(source, argVal);
                                    return 1;
                                }
                                return 0;
                            }
                    );
                    executesRequired.invoke(argNode, greedyCmdProxy);
                    thenMethod.invoke(literalBuilder, argNode);
                }

                registerMethod.invoke(dispatcher, literalBuilder);
            } catch (Throwable t) {
                Logger.error("BrigadierAdapter: Failed to register command '/" + name + "'", t);
            }
        }
    }

    /**
     * Broadcasts the updated Brigadier command tree to all online players on the server.
     */
    public static void syncCommandsToAll(Object server) {
        if (server == null) return;
        try {
            Object playerList = MethodHandleCache.findVirtualByName(server.getClass(), "getPlayerList").invoke(server);
            if (playerList == null) return;

            Object playersObj = MethodHandleCache.findVirtualByName(playerList.getClass(), "getPlayers").invoke(playerList);
            if (!(playersObj instanceof Iterable<?> players)) return;

            Object commands = MethodHandleCache.findVirtualByName(server.getClass(), "getCommands").invoke(server);
            if (commands == null) return;

            int count = 0;
            for (Object player : players) {
                try {
                    MethodHandleCache.findVirtualByName(commands.getClass(), "sendCommands", player.getClass()).invoke(commands, player);
                    count++;
                } catch (Throwable t) {
                    Logger.warn("Failed to push commands to player " + player + ": " + t.getMessage());
                }
            }
            if (count > 0) {
                Logger.info("BrigadierAdapter: Pushed updated Brigadier command tree to " + count + " connected player(s).");
            }
        } catch (Throwable t) {
            Logger.warn("BrigadierAdapter: Could not push command updates to players: " + t.getMessage());
        }
    }
}
