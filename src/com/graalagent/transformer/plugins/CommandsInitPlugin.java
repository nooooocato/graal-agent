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
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * 服务端命令微插件：在 net.minecraft.commands.Commands 构造完成返回前，
 * 截获 CommandDispatcher 并注入 /js、/reloadjs 及脚本注册的自定义服务端命令。
 */
public class CommandsInitPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/commands/Commands";
    private static final ClassDesc CD_COMMANDS = ClassDesc.of("net.minecraft.commands.Commands");
    private static final ClassDesc CD_COMMAND_SELECTION = ClassDesc.of("net.minecraft.commands.Commands$CommandSelection");
    private static final ClassDesc CD_COMMAND_BUILD_CONTEXT = ClassDesc.of("net.minecraft.commands.CommandBuildContext");
    private static final ClassDesc CD_COMMAND_DISPATCHER = ClassDesc.of("com.mojang.brigadier.CommandDispatcher");
    private static final ClassDesc CD_AGENT_BRIDGE = ClassDesc.of("com.graalagent.bridge.AgentBridge");

    private static final MethodTypeDesc MTD_INIT = MethodTypeDesc.of(ConstantDescs.CD_void, CD_COMMAND_SELECTION, CD_COMMAND_BUILD_CONTEXT);
    private static final MethodTypeDesc MTD_GET_DISPATCHER = MethodTypeDesc.of(CD_COMMAND_DISPATCHER);
    private static final MethodTypeDesc MTD_ON_REGISTER_COMMANDS = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);

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
                Logger.info("[CommandsInitPlugin] Injecting server Brigadier command dispatcher hook...");
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (ce instanceof ReturnInstruction) {
                            cb.aload(0);
                            cb.invokevirtual(CD_COMMANDS, "getDispatcher", MTD_GET_DISPATCHER);
                            cb.invokestatic(CD_AGENT_BRIDGE, "onRegisterServerCommands", MTD_ON_REGISTER_COMMANDS);
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
