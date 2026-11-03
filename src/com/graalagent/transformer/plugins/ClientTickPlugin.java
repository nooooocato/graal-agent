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
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * 客户端主心跳微插件：基于 SMV 虚拟化 net.minecraft.client.Minecraft.tick()V，
 * 在保持原版 tick 纯净执行的同时，前置派发 Agent 心跳与事件总线调度。
 */
public class ClientTickPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/Minecraft";
    private static final ClassDesc CD_MINECRAFT = ClassDesc.of("net.minecraft.client.Minecraft");
    private static final ClassDesc CD_AGENT_BRIDGE = ClassDesc.of("com.graalagent.bridge.AgentBridge");
    private static final MethodTypeDesc MTD_VOID = MethodTypeDesc.of(ConstantDescs.CD_void);

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
                    && mm.methodName().equalsString("tick")
                    && mm.methodTypeSymbol().equals(MTD_VOID)) {
                Logger.info("[ClientTickPlugin] SMV Virtualizing Minecraft.tick()...");
                MethodVirtualizer.virtualize(
                        builder,
                        mm,
                        "$$shadow$tick",
                        MethodVirtualizer.createPrefixDispatcher(
                                CD_MINECRAFT,
                                "$$shadow$tick",
                                mm.methodTypeSymbol(),
                                false,
                                cb -> cb.invokestatic(
                                        CD_AGENT_BRIDGE,
                                        "onTick",
                                        MTD_VOID
                                )
                        )
                );
                return;
            }
            builder.with(element);
        };
    }
}
