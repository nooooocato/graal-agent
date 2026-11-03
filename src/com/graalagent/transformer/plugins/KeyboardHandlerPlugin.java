package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import com.graalagent.transformer.smv.MethodVirtualizer;
import com.graalagent.util.Logger;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

/**
 * 客户端按键处理微插件：基于 SMV 虚拟化 net.minecraft.client.KeyboardHandler.keyPress(...)V，
 * 支持在按键被 JavaScript 脚本消费时完全取消并阻断原版按键响应，未消费时透明进入影子方法。
 */
public class KeyboardHandlerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/KeyboardHandler";
    private static final ClassDesc CD_KEYBOARD_HANDLER = ClassDesc.of("net.minecraft.client.KeyboardHandler");
    private static final ClassDesc CD_KEY_EVENT = ClassDesc.of("net.minecraft.client.input.KeyEvent");
    private static final ClassDesc CD_AGENT_BRIDGE = ClassDesc.of("com.graalagent.bridge.AgentBridge");
    private static final MethodTypeDesc MTD_KEY_PRESS = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_long, ConstantDescs.CD_int, CD_KEY_EVENT);
    private static final MethodTypeDesc MTD_ON_KEY = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_long, ConstantDescs.CD_int, ConstantDescs.CD_Object);

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
                    && mm.methodName().equalsString("keyPress")
                    && mm.methodTypeSymbol().equals(MTD_KEY_PRESS)) {
                Logger.info("[KeyboardHandlerPlugin] SMV Virtualizing KeyboardHandler.keyPress()...");
                MethodVirtualizer.virtualize(builder, mm, "$$shadow$keyPress", cb -> {
                    Label proceedLabel = cb.newLabel();
                    // 1. 调用 AgentBridge.onKey(long, int, Object) -> boolean
                    cb.lload(1);
                    cb.iload(3);
                    cb.aload(4);
                    cb.invokestatic(CD_AGENT_BRIDGE, "onKey", MTD_ON_KEY);
                    // 2. 若钩子返回 false (未消费)，跳转至影子方法执行原版响应
                    cb.ifeq(proceedLabel);
                    // 3. 若已被 JS 消费，直接返回，阻断原版按键响应！
                    cb.return_();

                    // 4. 执行原版影子逻辑
                    cb.labelBinding(proceedLabel);
                    MethodVirtualizer.invokeShadow(
                            cb,
                            CD_KEYBOARD_HANDLER,
                            "$$shadow$keyPress",
                            mm.methodTypeSymbol(),
                            false
                    );
                });
                return;
            }
            builder.with(element);
        };
    }
}
