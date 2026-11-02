package com.graalagent.transformer;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 统一微插件契约接口。
 * 每个标准微插件负责单个 Minecraft 类的字节码影子虚拟化与指令拦截。
 */
public interface ClassTransformPlugin {

    /**
     * 获取目标类的 JVM 内部名称（如 "net/minecraft/client/Minecraft"）
     */
    String getTargetClassName();

    /**
     * 针对目标类执行影子方法虚拟化或指令注入
     */
    void transform(ClassModel classModel, ClassBuilder classBuilder);
}
