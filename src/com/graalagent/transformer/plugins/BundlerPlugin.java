package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import com.graalagent.util.Logger;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.InvokeInstruction;

/**
 * 打包器类加载引导微插件：拦截 net.minecraft.bundler.Main.run([Ljava/lang/String;)V，
 * 移除 getParent() 调用，确保 Bundler 创建的 URLClassLoader 保留 AppClassLoader 作为父加载器，保证 Agent 与核心库对游戏可见。
 */
public class BundlerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/bundler/Main";

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
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("run")
                    && mm.methodType().stringValue().equals("([Ljava/lang/String;)V")) {
                Logger.info("[BundlerPlugin] Transforming Bundler URLClassLoader parent delegator...");
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (ce instanceof InvokeInstruction inv
                                && inv.owner().asInternalName().equals("java/lang/ClassLoader")
                                && inv.name().stringValue().equals("getParent")
                                && inv.type().stringValue().equals("()Ljava/lang/ClassLoader;")) {
                            Logger.info("[BundlerPlugin] Replaced Bundler URLClassLoader parent with AppClassLoader for Agent visibility.");
                            return; // 丢弃 getParent 调用，直接以当前 ClassLoader 为 parent
                        }
                        cb.with(ce);
                    }
                }));
                return;
            }
            builder.with(element);
        };
    }
}
