package com.graalagent.transformer.plugins;

import com.graalagent.test.Assert;
import com.graalagent.transformer.SMVOrchestrator;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;

public class ClientPluginsTest {

    private final ClassFile classFile = ClassFile.of();

    private byte[] createMockMinecraftClass() {
        return classFile.build(ClassDesc.of("net.minecraft.client.Minecraft"), cb -> {
            cb.withMethod("tick", MethodTypeDesc.ofDescriptor("()V"), AccessFlag.PUBLIC.mask(), mb -> {
                mb.withCode(codeBuilder -> codeBuilder.return_());
            });
        });
    }

    private byte[] createMockKeyboardHandlerClass() {
        return classFile.build(ClassDesc.of("net.minecraft.client.KeyboardHandler"), cb -> {
            cb.withMethod(
                    "keyPress",
                    MethodTypeDesc.ofDescriptor("(JILnet/minecraft/client/input/KeyEvent;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> codeBuilder.return_())
            );
        });
    }

    private byte[] createMockClientPacketListenerClass() {
        return classFile.build(ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener"), cb -> {
            cb.withField(
                    "commands",
                    ClassDesc.of("com.mojang.brigadier.CommandDispatcher"),
                    AccessFlag.PUBLIC.mask()
            );
            cb.withMethod(
                    "sendChat",
                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> codeBuilder.return_())
            );
            cb.withMethod(
                    "sendCommand",
                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> codeBuilder.return_())
            );
            cb.withMethod(
                    "handleCommands",
                    MethodTypeDesc.ofDescriptor("(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> codeBuilder.return_())
            );
        });
    }

    public void testClientTickPlugin() {
        ClientTickPlugin plugin = new ClientTickPlugin();
        Assert.assertEquals("net/minecraft/client/Minecraft", plugin.getTargetClassName());

        byte[] original = createMockMinecraftClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(plugin);

        byte[] transformed = orchestrator.transform(null, plugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed);

        ClassModel model = classFile.parse(transformed);
        boolean foundShadowTick = false;
        boolean foundOriginalTick = false;
        for (MethodModel mm : model.methods()) {
            String name = mm.methodName().stringValue();
            if (name.equals("$$shadow$tick")) {
                foundShadowTick = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow tick must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow tick must be synthetic");
            } else if (name.equals("tick")) {
                foundOriginalTick = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PUBLIC), "Dispatcher tick must be public");
            }
        }
        Assert.assertTrue(foundShadowTick, "Must contain $$shadow$tick");
        Assert.assertTrue(foundOriginalTick, "Must contain original tick dispatcher");
    }

    public void testKeyboardHandlerPlugin() {
        KeyboardHandlerPlugin plugin = new KeyboardHandlerPlugin();
        Assert.assertEquals("net/minecraft/client/KeyboardHandler", plugin.getTargetClassName());

        byte[] original = createMockKeyboardHandlerClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(plugin);

        byte[] transformed = orchestrator.transform(null, plugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed);

        ClassModel model = classFile.parse(transformed);
        boolean foundShadowKey = false;
        boolean foundDispatcherKey = false;
        for (MethodModel mm : model.methods()) {
            String name = mm.methodName().stringValue();
            if (name.equals("$$shadow$keyPress")) {
                foundShadowKey = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow keyPress must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow keyPress must be synthetic");
            } else if (name.equals("keyPress")) {
                foundDispatcherKey = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PUBLIC), "Dispatcher keyPress must be public");
            }
        }
        Assert.assertTrue(foundShadowKey, "Must contain $$shadow$keyPress");
        Assert.assertTrue(foundDispatcherKey, "Must contain keyPress dispatcher");
    }

    public void testPacketListenerPlugin() {
        PacketListenerPlugin plugin = new PacketListenerPlugin();
        Assert.assertEquals("net/minecraft/client/multiplayer/ClientPacketListener", plugin.getTargetClassName());

        byte[] original = createMockClientPacketListenerClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(plugin);

        byte[] transformed = orchestrator.transform(null, plugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed);

        ClassModel model = classFile.parse(transformed);
        boolean foundShadowChat = false;
        boolean foundDispatcherChat = false;
        boolean foundShadowCmd = false;
        boolean foundDispatcherCmd = false;
        boolean foundShadowHandle = false;
        boolean foundDispatcherHandle = false;

        for (MethodModel mm : model.methods()) {
            String name = mm.methodName().stringValue();
            if (name.equals("$$shadow$sendChat")) foundShadowChat = true;
            if (name.equals("sendChat")) foundDispatcherChat = true;
            if (name.equals("$$shadow$sendCommand")) foundShadowCmd = true;
            if (name.equals("sendCommand")) foundDispatcherCmd = true;
            if (name.equals("$$shadow$handleCommands")) foundShadowHandle = true;
            if (name.equals("handleCommands")) foundDispatcherHandle = true;
        }

        Assert.assertTrue(foundShadowChat, "Must contain $$shadow$sendChat");
        Assert.assertTrue(foundDispatcherChat, "Must contain sendChat dispatcher");
        Assert.assertTrue(foundShadowCmd, "Must contain $$shadow$sendCommand");
        Assert.assertTrue(foundDispatcherCmd, "Must contain sendCommand dispatcher");
        Assert.assertTrue(foundShadowHandle, "Must contain $$shadow$handleCommands");
        Assert.assertTrue(foundDispatcherHandle, "Must contain handleCommands dispatcher");
    }
}
