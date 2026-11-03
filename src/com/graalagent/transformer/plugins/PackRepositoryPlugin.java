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
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * 资源/数据包微插件：拦截 net.minecraft.server.packs.repository.PackRepository 的构造函数，
 * 动态向原版仓库源数组注入虚拟合成数据包目录源。
 */
public class PackRepositoryPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/server/packs/repository/PackRepository";
    private static final ClassDesc CD_REPOSITORY_SOURCE = ClassDesc.of("net.minecraft.server.packs.repository.RepositorySource");
    private static final ClassDesc CD_IMMUTABLE_SET = ClassDesc.of("com.google.common.collect.ImmutableSet");
    private static final ClassDesc CD_DATAPACK_MANAGER = ClassDesc.of("com.graalagent.datapack.DatapackManager");

    private static final MethodTypeDesc MTD_INIT = MethodTypeDesc.of(ConstantDescs.CD_void, CD_REPOSITORY_SOURCE.arrayType());
    private static final MethodTypeDesc MTD_INJECT_SOURCES = MethodTypeDesc.of(ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_Object.arrayType());

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
                    && mm.methodName().equalsString(ConstantDescs.INIT_NAME)
                    && mm.methodTypeSymbol().equals(MTD_INIT)) {
                Logger.info("[PackRepositoryPlugin] Injecting synthetic Datapack repository sources...");
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (ce instanceof InvokeInstruction inv
                                && inv.owner().asSymbol().equals(CD_IMMUTABLE_SET)
                                && inv.name().equalsString("copyOf")) {
                            cb.invokestatic(
                                    CD_DATAPACK_MANAGER,
                                    "injectSources",
                                    MTD_INJECT_SOURCES
                            );
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
