package com.mcagent.transformer;

import com.mcagent.script.ScriptManager;
import com.mcagent.util.Logger;

import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class MinecraftTransformer implements ClassFileTransformer {
    private final ClassFile classFile = ClassFile.of();

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }

        // Avoid instrumenting JDK / GraalVM internal runtime classes
        if (className.startsWith("java/") || className.startsWith("jdk/")
                || className.startsWith("sun/") || className.startsWith("com/oracle/")
                || className.startsWith("org/graalvm/") || className.startsWith("com/mcagent/")) {
            return null;
        }

        byte[] currentBytes = classfileBuffer;

        try {
            if (className.equals("net/minecraft/client/Minecraft")) {
                currentBytes = transformMinecraft(currentBytes);
            } else if (className.equals("net/minecraft/client/KeyboardHandler")) {
                currentBytes = transformKeyboardHandler(currentBytes);
            } else if (className.equals("net/minecraft/client/multiplayer/ClientPacketListener")) {
                currentBytes = transformClientPacketListener(currentBytes);
            } else if (className.equals("net/minecraft/server/MinecraftServer")) {
                currentBytes = transformMinecraftServer(currentBytes);
            }

            // Custom JavaScript transformers (delegated through ScriptManager's lock)
            ScriptManager sm = ScriptManager.getInstance();
            if (sm != null) {
                byte[] customResult = sm.applyCustomTransformers(className, currentBytes);
                if (customResult != null) {
                    currentBytes = customResult;
                }
            }

            if (currentBytes != classfileBuffer) {
                return currentBytes;
            }
        } catch (Throwable t) {
            Logger.error("Failed to transform class: " + className, t);
        }

        return null;
    }

    private byte[] transformMinecraft(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.client.Minecraft (tick hook)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("tick")
                    && mm.methodType().stringValue().equals("()V")) {
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    boolean injected = false;
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (!injected) {
                            injected = true;
                            cb.invokestatic(
                                    ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                    "onTick",
                                    MethodTypeDesc.ofDescriptor("()V")
                            );
                        }
                        cb.with(ce);
                    }
                }));
                return;
            }
            builder.with(element);
        };
        return classFile.transformClass(cm, ct);
    }

    private byte[] transformKeyboardHandler(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.client.KeyboardHandler (keyPress hook)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("keyPress")
                    && mm.methodType().stringValue().equals("(JILnet/minecraft/client/input/KeyEvent;)V")) {
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    boolean injected = false;
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (!injected) {
                            injected = true;
                            Label continueLabel = cb.newLabel();
                            cb.lload(1);
                            cb.iload(3);
                            cb.aload(4);
                            cb.invokestatic(
                                    ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                    "onKey",
                                    MethodTypeDesc.ofDescriptor("(JILjava/lang/Object;)Z")
                            );
                            cb.ifeq(continueLabel);
                            cb.return_();
                            cb.labelBinding(continueLabel);
                        }
                        cb.with(ce);
                    }
                }));
                return;
            }
            builder.with(element);
        };
        return classFile.transformClass(cm, ct);
    }

    private byte[] transformClientPacketListener(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.client.multiplayer.ClientPacketListener (commands & chat hook)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm) {
                String methodName = mm.methodName().stringValue();
                String methodDesc = mm.methodType().stringValue();

                // 1. sendChat hook
                if (methodName.equals("sendChat") && methodDesc.equals("(Ljava/lang/String;)V")) {
                    builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                        boolean injected = false;
                        @Override
                        public void accept(CodeBuilder cb, CodeElement ce) {
                            if (!injected) {
                                injected = true;
                                Label continueLabel = cb.newLabel();
                                cb.aload(1);
                                cb.invokestatic(
                                        ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                        "onSendChat",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z")
                                );
                                cb.ifeq(continueLabel);
                                cb.return_();
                                cb.labelBinding(continueLabel);
                            }
                            cb.with(ce);
                        }
                    }));
                    return;
                }

                // 2. sendCommand hook (normal /command pipeline)
                if (methodName.equals("sendCommand") && methodDesc.equals("(Ljava/lang/String;)V")) {
                    builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                        boolean injected = false;
                        @Override
                        public void accept(CodeBuilder cb, CodeElement ce) {
                            if (!injected) {
                                injected = true;
                                Label continueLabel = cb.newLabel();
                                cb.aload(1);
                                cb.invokestatic(
                                        ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                        "onSendCommand",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z")
                                );
                                cb.ifeq(continueLabel);
                                cb.return_();
                                cb.labelBinding(continueLabel);
                            }
                            cb.with(ce);
                        }
                    }));
                    return;
                }

                // 3. handleCommands hook (register /js into client Brigadier suggestions)
                if (methodName.equals("handleCommands")
                        && methodDesc.equals("(Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;)V")) {
                    builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                        @Override
                        public void accept(CodeBuilder cb, CodeElement ce) {
                            if (ce instanceof ReturnInstruction) {
                                cb.aload(0);
                                cb.getfield(
                                        ClassDesc.of("net.minecraft.client.multiplayer.ClientPacketListener"),
                                        "commands",
                                        ClassDesc.of("com.mojang.brigadier.CommandDispatcher")
                                );
                                cb.invokestatic(
                                        ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                        "onUpdateCommands",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                                );
                            }
                            cb.with(ce);
                        }
                    }));
                    return;
                }
            }
            builder.with(element);
        };
        return classFile.transformClass(cm, ct);
    }

    private byte[] transformMinecraftServer(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.server.MinecraftServer (lifecycle & tick hooks)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm) {
                String methodName = mm.methodName().stringValue();
                String methodDesc = mm.methodType().stringValue();

                // 1. runServer hook (server thread entry point and exit)
                if (methodName.equals("runServer") && methodDesc.equals("()V")) {
                    builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                        boolean injected = false;
                        @Override
                        public void accept(CodeBuilder cb, CodeElement ce) {
                            if (!injected) {
                                injected = true;
                                cb.aload(0);
                                cb.invokestatic(
                                        ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                        "onServerStart",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                                );
                            }
                            if (ce instanceof ReturnInstruction) {
                                cb.invokestatic(
                                        ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                        "onServerStop",
                                        MethodTypeDesc.ofDescriptor("()V")
                                );
                            }
                            cb.with(ce);
                        }
                    }));
                    return;
                }

                // 2. tickServer hook
                if (methodName.equals("tickServer") && methodDesc.equals("(Ljava/util/function/BooleanSupplier;)V")) {
                    builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                        boolean injected = false;
                        @Override
                        public void accept(CodeBuilder cb, CodeElement ce) {
                            if (!injected) {
                                injected = true;
                                cb.aload(0);
                                cb.invokestatic(
                                        ClassDesc.of("com.mcagent.bridge.AgentBridge"),
                                        "onServerTick",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                                );
                            }
                            cb.with(ce);
                        }
                    }));
                    return;
                }
            }
            builder.with(element);
        };
        return classFile.transformClass(cm, ct);
    }
}
