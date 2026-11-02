package com.graalagent.transformer;

import com.graalagent.script.ScriptManager;
import com.graalagent.util.Logger;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.InvokeInstruction;
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
                || className.startsWith("org/graalvm/") || className.startsWith("com/graalagent/")) {
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
            } else if (className.equals("net/minecraft/commands/Commands")) {
                currentBytes = transformCommands(currentBytes);
            } else if (className.equals("net/minecraft/bundler/Main")) {
                currentBytes = transformBundlerMain(currentBytes);
            } else if (className.equals("net/minecraft/server/packs/repository/PackRepository")) {
                currentBytes = transformPackRepository(currentBytes);
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
                                    ClassDesc.of("com.graalagent.bridge.AgentBridge"),
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
                                    ClassDesc.of("com.graalagent.bridge.AgentBridge"),
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
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
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
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
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
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
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
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                        "onServerStart",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                                );
                            }
                            if (ce instanceof ReturnInstruction) {
                                cb.invokestatic(
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
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
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                        "onServerTick",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V")
                                );
                            }
                            cb.with(ce);
                        }
                    }));
                    return;
                }

                // 3. reloadResources hook (trigger JS reload when vanilla datapack reload completes)
                if (methodName.equals("reloadResources")
                        && methodDesc.equals("(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;")) {
                    builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                        @Override
                        public void accept(CodeBuilder cb, CodeElement ce) {
                            if (ce instanceof ReturnInstruction) {
                                cb.invokestatic(
                                        ClassDesc.of("com.graalagent.bridge.AgentBridge"),
                                        "onServerReloadResources",
                                        MethodTypeDesc.ofDescriptor("(Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;")
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

    private byte[] transformCommands(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.commands.Commands (server command dispatcher hook)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("<init>")
                    && mm.methodType().stringValue().equals("(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V")) {
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
        return classFile.transformClass(cm, ct);
    }

    private byte[] transformBundlerMain(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.bundler.Main (parent classloader hook)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("run")
                    && mm.methodType().stringValue().equals("([Ljava/lang/String;)V")) {
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (ce instanceof InvokeInstruction inv
                                && inv.owner().asInternalName().equals("java/lang/ClassLoader")
                                && inv.name().stringValue().equals("getParent")
                                && inv.type().stringValue().equals("()Ljava/lang/ClassLoader;")) {
                            Logger.info("Replaced Bundler URLClassLoader parent with AppClassLoader for Agent visibility.");
                            return;
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

    private byte[] transformPackRepository(byte[] bytes) {
        Logger.info("Instrumenting net.minecraft.server.packs.repository.PackRepository (datapack source hook)...");
        ClassModel cm = classFile.parse(bytes);
        ClassTransform ct = (builder, element) -> {
            if (element instanceof MethodModel mm
                    && mm.methodName().stringValue().equals("<init>")
                    && mm.methodType().stringValue().equals("([Lnet/minecraft/server/packs/repository/RepositorySource;)V")) {
                builder.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                    @Override
                    public void accept(CodeBuilder cb, CodeElement ce) {
                        if (ce instanceof InvokeInstruction inv
                                && inv.owner().asInternalName().equals("com/google/common/collect/ImmutableSet")
                                && inv.name().stringValue().equals("copyOf")) {
                            cb.invokestatic(
                                    ClassDesc.of("com.graalagent.datapack.DatapackManager"),
                                    "injectSources",
                                    MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)[Ljava/lang/Object;")
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
}
