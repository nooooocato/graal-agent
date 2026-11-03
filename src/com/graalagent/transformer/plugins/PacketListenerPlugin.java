package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import com.graalagent.transformer.smv.MethodVirtualizer;
import com.graalagent.util.Logger;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * 客户端数据包监听微插件：基于 SMV 虚拟化 ClientPacketListener 的 sendChat、sendCommand 与 handleCommands。
 * 支持单人/多人模式下截获脚本前缀命令与注入客户端 Brigadier 提示树。
 */
public class PacketListenerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/multiplayer/ClientPacketListener";
    private static final ClassDesc CD_CLIENT_PACKET_LISTENER = ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener");
    private static final ClassDesc CD_COMMANDS_PACKET = ClassDesc.of("net.minecraft.network.protocol.game.ClientboundCommandsPacket");
    private static final ClassDesc CD_COMMAND_DISPATCHER = ClassDesc.of("com.mojang.brigadier.CommandDispatcher");
    private static final ClassDesc CD_AGENT_BRIDGE = ClassDesc.of("com.graalagent.bridge.AgentBridge");

    private static final MethodTypeDesc MTD_SEND_STRING = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_HANDLE_COMMANDS = MethodTypeDesc.of(ConstantDescs.CD_void, CD_COMMANDS_PACKET);
    private static final MethodTypeDesc MTD_ON_STRING_PREDICATE = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_String);
    private static final MethodTypeDesc MTD_ON_UPDATE_COMMANDS = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        ClassTransform ct = asClassTransform(classModel);
        for (ClassElement ce : classModel) {
            ct.accept(classBuilder, ce);
        }
    }

    @Override
    public ClassTransform asClassTransform(ClassModel classModel) {
        return (builder, element) -> {
            if (element instanceof MethodModel mm) {
                // 1. sendChat hook (virtualize to $$shadow$sendChat)
                if (mm.methodName().equalsString("sendChat") && mm.methodTypeSymbol().equals(MTD_SEND_STRING)) {
                    Logger.info("[PacketListenerPlugin] SMV Virtualizing ClientPacketListener.sendChat()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$sendChat", cb -> {
                        Label proceedLabel = cb.newLabel();
                        cb.aload(1);
                        cb.invokestatic(CD_AGENT_BRIDGE, "onSendChat", MTD_ON_STRING_PREDICATE);
                        cb.ifeq(proceedLabel);
                        cb.return_(); // 已被 JS 截获消费
                        cb.labelBinding(proceedLabel);
                        MethodVirtualizer.invokeShadow(
                                cb,
                                CD_CLIENT_PACKET_LISTENER,
                                "$$shadow$sendChat",
                                mm.methodTypeSymbol(),
                                false
                        );
                    });
                    return;
                }

                // 2. sendCommand hook (virtualize to $$shadow$sendCommand)
                if (mm.methodName().equalsString("sendCommand") && mm.methodTypeSymbol().equals(MTD_SEND_STRING)) {
                    Logger.info("[PacketListenerPlugin] SMV Virtualizing ClientPacketListener.sendCommand()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$sendCommand", cb -> {
                        Label proceedLabel = cb.newLabel();
                        cb.aload(1);
                        cb.invokestatic(CD_AGENT_BRIDGE, "onSendCommand", MTD_ON_STRING_PREDICATE);
                        cb.ifeq(proceedLabel);
                        cb.return_(); // 已被 JS 截获消费
                        cb.labelBinding(proceedLabel);
                        MethodVirtualizer.invokeShadow(
                                cb,
                                CD_CLIENT_PACKET_LISTENER,
                                "$$shadow$sendCommand",
                                mm.methodTypeSymbol(),
                                false
                        );
                    });
                    return;
                }

                // 3. handleCommands hook (virtualize to $$shadow$handleCommands)
                if (mm.methodName().equalsString("handleCommands") && mm.methodTypeSymbol().equals(MTD_HANDLE_COMMANDS)) {
                    Logger.info("[PacketListenerPlugin] SMV Virtualizing ClientPacketListener.handleCommands()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$handleCommands", cb -> {
                        // 先执行完整的原版影子解析逻辑
                        MethodVirtualizer.emitShadowInvocation(
                                cb,
                                CD_CLIENT_PACKET_LISTENER,
                                "$$shadow$handleCommands",
                                mm.methodTypeSymbol(),
                                false
                        );
                        // 原版执行完毕后，注入客户端 /js 命令提示
                        cb.aload(0);
                        cb.getfield(
                                CD_CLIENT_PACKET_LISTENER,
                                "commands",
                                CD_COMMAND_DISPATCHER
                        );
                        cb.invokestatic(CD_AGENT_BRIDGE, "onUpdateCommands", MTD_ON_UPDATE_COMMANDS);
                        cb.return_();
                    });
                    return;
                }
            }
            builder.with(element);
        };
    }
}
