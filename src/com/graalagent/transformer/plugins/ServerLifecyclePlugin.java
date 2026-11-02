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
 * 服务端生命周期微插件：基于 SMV 虚拟化 net.minecraft.server.MinecraftServer 的 runServer 与 reloadResources。
 * 捕获服务端启动、安全终止，以及在原版数据包重载完成时自动触发 JS 脚本热重载。
 */
public class ServerLifecyclePlugin implements ClassTransformPlugin {

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
            if (element instanceof MethodModel mm) {
                String methodName = mm.methodName().stringValue();
                String methodDesc = mm.methodType().stringValue();

                // 1. runServer hook (virtualize to $$shadow$runServer)
                if (methodName.equals("runServer") && methodDesc.equals("()V")) {
                    Logger.info("[ServerLifecyclePlugin] SMV Virtualizing MinecraftServer.runServer()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$runServer", cb -> {
                        // Prefix: onServerStart(this)
                        cb.aload(0);
                        cb.invokestatic(
                                ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                "onServerStart",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                        );

                        // Around: 调用影子方法
                        MethodVirtualizer.emitShadowInvocation(
                                cb,
                                ClassDesc.of("net.minecraft.server.MinecraftServer"),
                                "$$shadow$runServer",
                                mm.methodTypeSymbol(),
                                false
                        );

                        // Postfix: onServerStop()
                        cb.invokestatic(
                                ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                "onServerStop",
                                MethodTypeDesc.ofDescriptor("()V")
                        );
                        cb.return_();
                    });
                    return;
                }

                // 2. reloadResources hook (virtualize to $$shadow$reloadResources)
                if (methodName.equals("reloadResources")
                        && methodDesc.equals("(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;")) {
                    Logger.info("[ServerLifecyclePlugin] SMV Virtualizing MinecraftServer.reloadResources()...");
                    MethodVirtualizer.virtualize(builder, mm, "$$shadow$reloadResources", cb -> {
                        // 调用影子方法获得原始 CompletableFuture
                        MethodVirtualizer.emitShadowInvocation(
                                cb,
                                ClassDesc.of("net.minecraft.server.MinecraftServer"),
                                "$$shadow$reloadResources",
                                mm.methodTypeSymbol(),
                                false
                        );
                        // 包装返回值触发 JS 自动热重载
                        cb.invokestatic(
                                ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                "onServerReloadResources",
                                MethodTypeDesc.ofDescriptor("(Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;")
                        );
                        cb.areturn();
                    });
                    return;
                }
            }
            builder.with(element);
        };
    }
}
