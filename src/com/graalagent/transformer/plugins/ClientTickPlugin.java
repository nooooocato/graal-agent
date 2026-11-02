package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import com.graalagent.transformer.smv.MethodVirtualizer;
import com.graalagent.util.Logger;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * 客户端主心跳微插件：基于 SMV 虚拟化 net.minecraft.client.Minecraft.tick()V，
 * 在保持原版 tick 纯净执行的同时，前置派发 Agent 心跳与事件总线调度。
 */
public class ClientTickPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/Minecraft";

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
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("tick")
                    && mm.methodType().stringValue().equals("()V")) {
                Logger.info("[ClientTickPlugin] SMV Virtualizing Minecraft.tick()...");
                MethodVirtualizer.virtualize(
                        builder,
                        mm,
                        "$$shadow$tick",
                        MethodVirtualizer.createPrefixDispatcher(
                                ClassDesc.of("net.minecraft.client.Minecraft"),
                                "$$shadow$tick",
                                mm.methodTypeSymbol(),
                                false,
                                cb -> cb.invokestatic(
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                        "onTick",
                                        MethodTypeDesc.ofDescriptor("()V")
                                )
                        )
                );
                return;
            }
            builder.with(element);
        };
    }
}
