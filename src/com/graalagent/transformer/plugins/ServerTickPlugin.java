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
 * 服务端主心跳微插件：基于 SMV 虚拟化 net.minecraft.server.MinecraftServer.tickServer(...)，
 * 驱动服务端每 tick 的主事件调度、命令同步与异步事务提交。
 */
public class ServerTickPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/server/MinecraftServer";
    private static final ClassDesc CD_SERVER = ClassDesc.of("net.minecraft.server.MinecraftServer");
    private static final ClassDesc CD_AGENT_BRIDGE = ClassDesc.of("com.graalagent.bridge.AgentBridge");
    private static final ClassDesc CD_BOOLEAN_SUPPLIER = ClassDesc.of("java.util.function.BooleanSupplier");

    private static final MethodTypeDesc MTD_TICK_SERVER = MethodTypeDesc.of(ConstantDescs.CD_void, CD_BOOLEAN_SUPPLIER);
    private static final MethodTypeDesc MTD_ON_SERVER_TICK = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);

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
                    && mm.methodName().equalsString("tickServer")
                    && mm.methodTypeSymbol().equals(MTD_TICK_SERVER)) {
                Logger.info("[ServerTickPlugin] SMV Virtualizing MinecraftServer.tickServer()...");
                MethodVirtualizer.virtualize(builder, mm, "$$shadow$tickServer", cb -> {
                    // Prefix: onServerTick(this)
                    cb.aload(0);
                    cb.invokestatic(CD_AGENT_BRIDGE, "onServerTick", MTD_ON_SERVER_TICK);

                    // Around: 调用影子方法
                    MethodVirtualizer.invokeShadow(
                            cb,
                            CD_SERVER,
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
