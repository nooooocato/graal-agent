package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 客户端按键微插件：虚拟化 net.minecraft.client.KeyboardHandler.keyPress(...)V，
 * 支持按键消费阻断与影子透传。
 */
public class KeyboardHandlerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/KeyboardHandler";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("KeyboardHandlerPlugin is not implemented yet (Red Phase stub)");
    }
}
