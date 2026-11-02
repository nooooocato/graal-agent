package com.graalagent.transformer;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;

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
     * 针对目标类执行影子方法虚拟化或指令注入。
     * 当插件实现此方法时，SMVOrchestrator 会自动保留所有未被插件修改的类元素（字段、属性、其余方法）。
     */
    void transform(ClassModel classModel, ClassBuilder classBuilder);

    /**
     * 可选：若插件需要全量控制 ClassTransform 遍历管道，可重写此方法返回原生 ClassTransform。
     * 默认返回 null，使用 transform(ClassModel, ClassBuilder) 简化模式。
     */
    default ClassTransform asClassTransform(ClassModel classModel) {
        return null;
    }
}
