package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 客户端心跳微插件：虚拟化 net.minecraft.client.Minecraft.tick()V
 */
public class ClientTickPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/Minecraft";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("ClientTickPlugin is not implemented yet (Red Phase stub)");
    }
}
