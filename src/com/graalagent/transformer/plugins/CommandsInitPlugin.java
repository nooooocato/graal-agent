package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 服务端指令总线微插件：拦截 net.minecraft.commands.Commands 的构造函数注入 Brigadier 命令
 */
public class CommandsInitPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/commands/Commands";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("CommandsInitPlugin is not yet implemented (Red Phase stub)");
    }
}
