package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 服务端生命周期微插件：基于 SMV 虚拟化 net.minecraft.server.MinecraftServer 的 runServer 与 reloadResources
 */
public class ServerLifecyclePlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/server/MinecraftServer";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("ServerLifecyclePlugin is not yet implemented (Red Phase stub)");
    }
}
