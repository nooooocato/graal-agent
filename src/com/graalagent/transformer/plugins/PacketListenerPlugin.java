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
import java.lang.constant.MethodTypeDesc;

/**
 * 客户端数据包监听微插件：基于 SMV 虚拟化 ClientPacketListener 的 sendChat、sendCommand 与 handleCommands。
 * 支持单人/多人模式下截获脚本前缀命令与注入客户端 Brigadier 提示树。
 */
public class PacketListenerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/multiplayer/ClientPacketListener";

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
                String methodName = mm.methodName().stringValue();
                String methodDesc = mm.methodType().stringValue();

                // 1. sendChat hook (virtualize to $$shadow$sendChat)
                if (methodName.equals("sendChat") && methodDesc.equals("(Ljava/lang/String;)V")) {
                    Logger.info("[PacketListenerPlugin] SMV Virtualizing ClientPacketListener.sendChat()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$sendChat", cb -> {
                        Label proceedLabel = cb.newLabel();
                        cb.aload(1);
                        cb.invokestatic(
                                ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                "onSendChat",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z")
                        );
                        cb.ifeq(proceedLabel);
                        cb.return_(); // 已被 JS 截获消费
                        cb.labelBinding(proceedLabel);
                        MethodVirtualizer.invokeShadow(
                                cb,
                                ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener"),
                                "$$shadow$sendChat",
                                mm.methodTypeSymbol(),
                                false
                        );
                    });
                    return;
                }

                // 2. sendCommand hook (virtualize to $$shadow$sendCommand)
                if (methodName.equals("sendCommand") && methodDesc.equals("(Ljava/lang/String;)V")) {
                    Logger.info("[PacketListenerPlugin] SMV Virtualizing ClientPacketListener.sendCommand()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$sendCommand", cb -> {
                        Label proceedLabel = cb.newLabel();
                        cb.aload(1);
                        cb.invokestatic(
                                ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                "onSendCommand",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z")
                        );
                        cb.ifeq(proceedLabel);
                        cb.return_(); // 已被 JS 截获消费
                        cb.labelBinding(proceedLabel);
                        MethodVirtualizer.invokeShadow(
                                cb,
                                ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener"),
                                "$$shadow$sendCommand",
                                mm.methodTypeSymbol(),
                                false
                        );
                    });
                    return;
                }

                // 3. handleCommands hook (virtualize to $$shadow$handleCommands)
                if (methodName.equals("handleCommands")
                        && methodDesc.equals("(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V")) {
                    Logger.info("[PacketListenerPlugin] SMV Virtualizing ClientPacketListener.handleCommands()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$handleCommands", cb -> {
                        // 先执行完整的原版影子解析逻辑
                        MethodVirtualizer.emitShadowInvocation(
                                cb,
                                ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener"),
                                "$$shadow$handleCommands",
                                mm.methodTypeSymbol(),
                                false
                        );
                        // 原版执行完毕后，注入客户端 /js 命令提示
                        cb.aload(0);
                        cb.getfield(
                                ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener"),
                                "commands",
                                ClassDesc.of("com.mojang.brigadier.CommandDispatcher")
                        );
                        cb.invokestatic(
                                ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                "onUpdateCommands",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                        );
                        cb.return_();
                    });
                    return;
                }
            }
            builder.with(element);
        };
    }
}
