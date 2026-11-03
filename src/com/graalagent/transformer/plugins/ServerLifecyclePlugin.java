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
 * 服务端生命周期微插件：基于 SMV 虚拟化 net.minecraft.server.MinecraftServer 的 runServer 与 reloadResources。
 * 捕获服务端启动、安全终止，以及在原版数据包重载完成时自动触发 JS 脚本热重载。
 */
public class ServerLifecyclePlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/server/MinecraftServer";
    private static final ClassDesc CD_SERVER = ClassDesc.of("net.minecraft.server.MinecraftServer");
    private static final ClassDesc CD_AGENT_BRIDGE = ClassDesc.of("com.graalagent.bridge.AgentBridge");
    private static final ClassDesc CD_COMPLETABLE_FUTURE = ClassDesc.of("java.util.concurrent.CompletableFuture");
    private static final ClassDesc CD_COLLECTION = ClassDesc.of("java.util.Collection");

    private static final MethodTypeDesc MTD_RUN_SERVER = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc MTD_ON_SERVER_START = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);
    private static final MethodTypeDesc MTD_ON_SERVER_STOP = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc MTD_RELOAD_RESOURCES = MethodTypeDesc.of(CD_COMPLETABLE_FUTURE, CD_COLLECTION);
    private static final MethodTypeDesc MTD_ON_RELOAD_RESOURCES = MethodTypeDesc.of(CD_COMPLETABLE_FUTURE, CD_COMPLETABLE_FUTURE);

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
                // 1. runServer hook (virtualize to $$shadow$runServer)
                if (mm.methodName().equalsString("runServer") && mm.methodTypeSymbol().equals(MTD_RUN_SERVER)) {
                    Logger.info("[ServerLifecyclePlugin] SMV Virtualizing MinecraftServer.runServer()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$runServer", cb -> {
                        // Prefix: onServerStart(this)
                        cb.aload(0);
                        cb.invokestatic(CD_AGENT_BRIDGE, "onServerStart", MTD_ON_SERVER_START);

                        // Around: 调用影子方法
                        MethodVirtualizer.emitShadowInvocation(
                                cb,
                                CD_SERVER,
                                "$$shadow$runServer",
                                mm.methodTypeSymbol(),
                                false
                        );

                        // Postfix: onServerStop()
                        cb.invokestatic(CD_AGENT_BRIDGE, "onServerStop", MTD_ON_SERVER_STOP);
                        cb.return_();
                    });
                    return;
                }

                // 2. reloadResources hook (virtualize to $$shadow$reloadResources)
                if (mm.methodName().equalsString("reloadResources") && mm.methodTypeSymbol().equals(MTD_RELOAD_RESOURCES)) {
                    Logger.info("[ServerLifecyclePlugin] SMV Virtualizing MinecraftServer.reloadResources()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$reloadResources", cb -> {
                        // 调用影子方法获得原始 CompletableFuture
                        MethodVirtualizer.emitShadowInvocation(
                                cb,
                                CD_SERVER,
                                "$$shadow$reloadResources",
                                mm.methodTypeSymbol(),
                                false
                        );
                        // 包装返回值触发 JS 自动热重载
                        cb.invokestatic(CD_AGENT_BRIDGE, "onServerReloadResources", MTD_ON_RELOAD_RESOURCES);
                        cb.areturn();
                    });
                    return;
                }
            }
            builder.with(element);
        };
    }
}
