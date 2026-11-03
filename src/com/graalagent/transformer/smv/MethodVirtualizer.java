package com.graalagent.transformer.smv;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 核心虚拟化器：基于 JDK 24+ java.lang.classfile 将目标方法克隆为私有合成影子方法 ($$shadow$<name>)，
 * 并将原公开方法槽位重写为虚拟分发桩 (Virtual Dispatcher)。
 */
public final class MethodVirtualizer {

    private MethodVirtualizer() {
        // Utility class
    }

    /**
     * 生成统一命名规则的影子方法名
     */
    public static String shadowName(String originalMethodName) {
        return "$$shadow$" + Objects.requireNonNull(originalMethodName, "originalMethodName");
    }

    /**
     * 核心虚拟化方法：将目标方法克隆为影子方法，并将原方法体替换为自定义分发逻辑。
     *
     * @param classBuilder 正在构建的类构建器
     * @param originalMethod 原始目标方法模型
     * @param shadowMethodName 影子方法的名称 (如 $$shadow$keyPress)
     * @param dispatcherLogic 原方法分发桩的字节码生成逻辑
     */
    public static void virtualize(
            ClassBuilder classBuilder,
            MethodModel originalMethod,
            String shadowMethodName,
            Consumer<CodeBuilder> dispatcherLogic
    ) {
        Objects.requireNonNull(classBuilder, "classBuilder");
        Objects.requireNonNull(originalMethod, "originalMethod");
        Objects.requireNonNull(shadowMethodName, "shadowMethodName");
        Objects.requireNonNull(dispatcherLogic, "dispatcherLogic");

        if (originalMethod.code().isEmpty()) {
            throw new IllegalArgumentException("Cannot virtualize method without code: "
                    + originalMethod.methodName().stringValue());
        }

        // 1. 克隆原方法为 private synthetic 影子方法，保留完整字节码与属性
        int originalFlags = originalMethod.flags().flagsMask();
        int shadowFlags = (originalFlags & ~AccessFlag.PUBLIC.mask() & ~AccessFlag.PROTECTED.mask())
                | AccessFlag.PRIVATE.mask() | AccessFlag.SYNTHETIC.mask();

        classBuilder.withMethod(
                shadowMethodName,
                originalMethod.methodTypeSymbol(),
                shadowFlags,
                mb -> {
                    for (MethodElement me : originalMethod) {
                        if (me instanceof CodeModel codeModel) {
                            mb.withCode(cb -> codeModel.forEach(cb::with));
                        } else if (!(me instanceof java.lang.classfile.AccessFlags)) {
                            mb.with(me);
                        }
                    }
                }
        );

        // 2. 将原方法的方法体替换为分发桩代码
        classBuilder.transformMethod(originalMethod, (mb, me) -> {
            if (me instanceof CodeModel) {
                mb.withCode(dispatcherLogic);
            } else {
                mb.with(me);
            }
        });
    }

    /**
     * 通用影子方法调用器：自动根据方法签名将接收者 (this) 与入参加载至操作数栈，
     * 发起 invokespecial / invokestatic 调用，并自动生成对应返回指令。
     */
    public static void invokeShadow(
            CodeBuilder cb,
            ClassDesc owner,
            String shadowMethodName,
            MethodTypeDesc methodType,
            boolean isStatic
    ) {
        emitShadowInvocation(cb, owner, shadowMethodName, methodType, isStatic);
        emitReturn(cb, methodType.returnType());
    }

    /**
     * 发射加载所有入参并调用影子方法的指令（不包含最后的 return 指令，方便后续处理返回值）。
     */
    public static void emitShadowInvocation(
            CodeBuilder cb,
            ClassDesc owner,
            String shadowMethodName,
            MethodTypeDesc methodType,
            boolean isStatic
    ) {
        Objects.requireNonNull(cb, "cb");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(shadowMethodName, "shadowMethodName");
        Objects.requireNonNull(methodType, "methodType");

        int slot = 0;
        if (!isStatic) {
            cb.aload(0); // 加载 this
            slot = 1;
        }

        for (ClassDesc paramDesc : methodType.parameterList()) {
            TypeKind tk = TypeKind.from(paramDesc);
            cb.loadLocal(tk.asLoadable(), slot);
            slot += tk.slotSize();
        }

        if (isStatic) {
            cb.invokestatic(owner, shadowMethodName, methodType);
        } else {
            cb.invokespecial(owner, shadowMethodName, methodType);
        }
    }

    /**
     * 根据返回类型描述符发射对应的 return 指令。
     */
    public static void emitReturn(CodeBuilder cb, ClassDesc returnTypeDesc) {
        TypeKind returnTk = TypeKind.from(returnTypeDesc);
        if (returnTk == TypeKind.VOID) {
            cb.return_();
        } else {
            cb.return_(returnTk.asLoadable());
        }
    }

    /**
     * 生成前置拦截分发桩：先执行 prefixLogic，随后调用影子方法并返回。
     */
    public static Consumer<CodeBuilder> createPrefixDispatcher(
            ClassDesc owner,
            String shadowMethodName,
            MethodTypeDesc methodType,
            boolean isStatic,
            Consumer<CodeBuilder> prefixLogic
    ) {
        return cb -> {
            if (prefixLogic != null) {
                prefixLogic.accept(cb);
            }
            invokeShadow(cb, owner, shadowMethodName, methodType, isStatic);
        };
    }

    /**
     * 生成直通转发分发桩：仅调用影子方法并返回。
     */
    public static Consumer<CodeBuilder> createForwardDispatcher(
            ClassDesc owner,
            String shadowMethodName,
            MethodTypeDesc methodType,
            boolean isStatic
    ) {
        return cb -> invokeShadow(cb, owner, shadowMethodName, methodType, isStatic);
    }
}
