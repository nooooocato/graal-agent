package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 资源/数据包微插件：拦截 net.minecraft.server.packs.repository.PackRepository 注入合成数据包源
 */
public class PackRepositoryPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/server/packs/repository/PackRepository";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("PackRepositoryPlugin is not yet implemented (Red Phase stub)");
    }
}
