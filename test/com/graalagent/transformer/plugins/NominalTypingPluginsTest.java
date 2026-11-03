package com.graalagent.transformer.plugins;

import com.graalagent.test.Assert;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NominalTypingPluginsTest {

    private final ClassFile cf = ClassFile.of();

    public void testBundlerPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                BundlerPlugin.class,
                List.of("([Ljava/lang/String;)V", "()Ljava/lang/ClassLoader;")
        );
    }

    public void testClientTickPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                ClientTickPlugin.class,
                List.of("()V")
        );
    }

    public void testKeyboardHandlerPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                KeyboardHandlerPlugin.class,
                List.of("(JILnet/minecraft/client/input/KeyEvent;)V", "(JILjava/lang/Object;)Z")
        );
    }

    public void testPacketListenerPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                PacketListenerPlugin.class,
                List.of(
                        "(Ljava/lang/String;)V",
                        "(Ljava/lang/String;)Z",
                        "(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V",
                        "(Ljava/lang/Object;)V"
                )
        );
    }

    public void testServerLifecyclePluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                ServerLifecyclePlugin.class,
                List.of(
                        "()V",
                        "(Ljava/lang/Object;)V",
                        "(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
                        "(Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;"
                )
        );
    }

    public void testServerTickPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                ServerTickPlugin.class,
                List.of(
                        "(Ljava/util/function/BooleanSupplier;)V",
                        "(Ljava/lang/Object;)V"
                )
        );
    }

    public void testCommandsInitPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                CommandsInitPlugin.class,
                List.of(
                        "(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V",
                        "()Lcom/mojang/brigadier/CommandDispatcher;",
                        "(Ljava/lang/Object;)V"
                )
        );
    }

    public void testPackRepositoryPluginNoRawDescriptors() throws Exception {
        assertNoForbiddenDescriptors(
                PackRepositoryPlugin.class,
                List.of(
                        "([Lnet/minecraft/server/packs/repository/RepositorySource;)V",
                        "([Ljava/lang/Object;)[Ljava/lang/Object;"
                )
        );
    }

    private void assertNoForbiddenDescriptors(Class<?> pluginClass, List<String> forbiddenDescriptors) throws Exception {
        String resourcePath = "/" + pluginClass.getName().replace('.', '/') + ".class";
        try (InputStream is = pluginClass.getResourceAsStream(resourcePath)) {
            Assert.assertNotNull(is, "Could not load bytecode for " + pluginClass.getName());
            ClassModel model = cf.parse(is.readAllBytes());

            Set<String> poolStrings = new java.util.HashSet<>();
            for (java.lang.classfile.constantpool.PoolEntry entry : model.constantPool()) {
                if (entry instanceof Utf8Entry utf8) {
                    poolStrings.add(utf8.stringValue());
                }
            }

            for (String forbidden : forbiddenDescriptors) {
                Assert.assertFalse(
                        poolStrings.contains(forbidden),
                        "Plugin " + pluginClass.getSimpleName()
                                + " must NOT contain raw descriptor string literal in bytecode constant pool: '"
                                + forbidden + "'"
                );
            }
        }
    }
}
