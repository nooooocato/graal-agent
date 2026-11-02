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
 * 服务端主心跳微插件：基于 SMV 虚拟化 net.minecraft.server.MinecraftServer.tickServer(...)，
 * 驱动服务端每 tick 的主事件调度、命令同步与异步事务提交。
 */
public class ServerTickPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/server/MinecraftServer";

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
                    && mm.methodName().stringValue().equals("tickServer")
                    && mm.methodType().stringValue().equals("(Ljava/util/function/BooleanSupplier;)V")) {
                Logger.info("[ServerTickPlugin] SMV Virtualizing MinecraftServer.tickServer()...");
                MethodVirtualizer.virtualize(builder, mm, "$$shadow$tickServer", cb -> {
                    // Prefix: onServerTick(this)
                    cb.aload(0);
                    cb.invokestatic(
                            ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                            "onServerTick",
                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                    );

                    // Around: 调用影子方法
                    MethodVirtualizer.invokeShadow(
                            cb,
                            ClassDesc.of("net.minecraft.server.MinecraftServer"),
                            "$$shadow$tickServer",
                            mm.methodTypeSymbol(),
                            false
                    );
                });
                return;
            }
            builder.with(element);
        };
    }
}
