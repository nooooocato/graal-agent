package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 打包引导微插件：修复 net.minecraft.bundler.Main 的 URLClassLoader 父子委托链
 */
public class BundlerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/bundler/Main";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("BundlerPlugin is not yet implemented (Red Phase stub)");
    }
}
