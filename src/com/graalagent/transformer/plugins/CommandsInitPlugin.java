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
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/**
 * 服务端命令微插件：在 net.minecraft.commands.Commands 构造完成返回前，
 * 截获 CommandDispatcher 并注入 /js、/reloadjs 及脚本注册的自定义服务端命令。
 */
public class CommandsInitPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/commands/Commands";

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
                    && mm.methodName().stringValue().equals("<init>")
                    && mm.methodType().stringValue().equals("(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V")) {
                Logger.info("[CommandsInitPlugin] Injecting server Brigadier command dispatcher hook...");
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (ce instanceof ReturnInstruction) {
                            cb.aload(0);
                            cb.invokevirtual(
                                    ClassDesc.of("net.minecraft.commands.Commands"),
                                    "getDispatcher",
                                    MethodTypeDesc.ofDescriptor("()Lcom/mojang/brigadier/CommandDispatcher;")
                            );
                            cb.invokestatic(
                                    ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                    "onRegisterServerCommands",
                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
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
