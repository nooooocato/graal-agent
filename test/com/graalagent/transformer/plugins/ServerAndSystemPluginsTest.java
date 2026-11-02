package com.graalagent.transformer.plugins;

import com.graalagent.test.Assert;
import com.graalagent.transformer.SMVOrchestrator;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;

public class ServerAndSystemPluginsTest {

    private final ClassFile classFile = ClassFile.of();

    private byte[] createMockMinecraftServerClass() {
        return classFile.build(ClassDesc.of("net.minecraft.server.MinecraftServer"), cb -> {
            cb.withMethod("runServer", MethodTypeDesc.ofDescriptor("()V"), AccessFlag.PUBLIC.mask(), mb -> {
                mb.withCode(codeBuilder -> codeBuilder.return_());
            });
            cb.withMethod(
                    "tickServer",
                    MethodTypeDesc.ofDescriptor("(Ljava/util/function/BooleanSupplier;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> codeBuilder.return_())
            );
            cb.withMethod(
                    "reloadResources",
                    MethodTypeDesc.ofDescriptor("(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> {
                        codeBuilder.aconst_null();
                        codeBuilder.areturn();
                    })
            );
        });
    }

    private byte[] createMockCommandsClass() {
        return classFile.build(ClassDesc.of("net.minecraft.commands.Commands"), cb -> {
            cb.withMethod(
                    "<init>",
                    MethodTypeDesc.ofDescriptor("(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> codeBuilder.return_())
            );
            cb.withMethod(
                    "getDispatcher",
                    MethodTypeDesc.ofDescriptor("()Lcom/mojang/brigadier/CommandDispatcher;"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> {
                        codeBuilder.aconst_null();
                        codeBuilder.areturn();
                    })
            );
        });
    }

    private byte[] createMockBundlerMainClass() {
        return classFile.build(ClassDesc.of("net.minecraft.bundler.Main"), cb -> {
            cb.withMethod(
                    "run",
                    MethodTypeDesc.ofDescriptor("([Ljava/lang/String;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> {
                        codeBuilder.aconst_null();
                        codeBuilder.invokevirtual(
                                ClassDesc.of("java.lang.ClassLoader"),
                                "getParent",
                                MethodTypeDesc.ofDescriptor("()Ljava/lang/ClassLoader;")
                        );
                        codeBuilder.pop();
                        codeBuilder.return_();
                    })
            );
        });
    }

    private byte[] createMockPackRepositoryClass() {
        return classFile.build(ClassDesc.of("net.minecraft.server.packs.repository.PackRepository"), cb -> {
            cb.withMethod(
                    "<init>",
                    MethodTypeDesc.ofDescriptor("([Lnet/minecraft/server/packs/repository/RepositorySource;)V"),
                    AccessFlag.PUBLIC.mask(),
                    mb -> mb.withCode(codeBuilder -> {
                        codeBuilder.aconst_null();
                        codeBuilder.invokestatic(
                                ClassDesc.of("com.google.common.collect.ImmutableSet"),
                                "copyOf",
                                MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)Lcom/google/common/collect/ImmutableSet;")
                        );
                        codeBuilder.pop();
                        codeBuilder.return_();
                    })
            );
        });
    }

    public void testServerLifecycleAndTickPlugins() {
        ServerLifecyclePlugin lifecyclePlugin = new ServerLifecyclePlugin();
        ServerTickPlugin tickPlugin = new ServerTickPlugin();
        Assert.assertEquals("net/minecraft/server/MinecraftServer", lifecyclePlugin.getTargetClassName());
        Assert.assertEquals("net/minecraft/server/MinecraftServer", tickPlugin.getTargetClassName());

        byte[] original = createMockMinecraftServerClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(lifecyclePlugin);
        orchestrator.registerPlugin(tickPlugin);

        byte[] transformed = orchestrator.transform(null, lifecyclePlugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed, "Transformed bytes should not be null");

        ClassModel model = classFile.parse(transformed);
        boolean foundShadowRun = false;
        boolean foundShadowTick = false;
        boolean foundShadowReload = false;

        for (MethodModel mm : model.methods()) {
            String name = mm.methodName().stringValue();
            if (name.equals("$$shadow$runServer")) {
                foundShadowRun = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow runServer must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow runServer must be synthetic");
            } else if (name.equals("$$shadow$tickServer")) {
                foundShadowTick = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow tickServer must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow tickServer must be synthetic");
            } else if (name.equals("$$shadow$reloadResources")) {
                foundShadowReload = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow reloadResources must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow reloadResources must be synthetic");
            }
        }

        Assert.assertTrue(foundShadowRun, "Must contain $$shadow$runServer");
        Assert.assertTrue(foundShadowTick, "Must contain $$shadow$tickServer");
        Assert.assertTrue(foundShadowReload, "Must contain $$shadow$reloadResources");
    }

    public void testCommandsInitPlugin() {
        CommandsInitPlugin plugin = new CommandsInitPlugin();
        Assert.assertEquals("net/minecraft/commands/Commands", plugin.getTargetClassName());

        byte[] original = createMockCommandsClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(plugin);

        byte[] transformed = orchestrator.transform(null, plugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed);

        ClassModel model = classFile.parse(transformed);
        boolean injected = false;
        for (MethodModel mm : model.methods()) {
            if (mm.methodName().stringValue().equals("<init>")) {
                for (java.lang.classfile.CodeElement ce : mm.code().orElseThrow()) {
                    if (ce instanceof InvokeInstruction inv
                            && inv.name().stringValue().equals("onRegisterServerCommands")) {
                        injected = true;
                        break;
                    }
                }
            }
        }
        Assert.assertTrue(injected, "Commands.<init> must contain onRegisterServerCommands injection");
    }

    public void testBundlerPlugin() {
        BundlerPlugin plugin = new BundlerPlugin();
        Assert.assertEquals("net/minecraft/bundler/Main", plugin.getTargetClassName());

        byte[] original = createMockBundlerMainClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(plugin);

        byte[] transformed = orchestrator.transform(null, plugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed);

        ClassModel model = classFile.parse(transformed);
        boolean foundGetParent = false;
        for (MethodModel mm : model.methods()) {
            if (mm.methodName().stringValue().equals("run")) {
                for (java.lang.classfile.CodeElement ce : mm.code().orElseThrow()) {
                    if (ce instanceof InvokeInstruction inv
                            && inv.name().stringValue().equals("getParent")) {
                        foundGetParent = true;
                        break;
                    }
                }
            }
        }
        Assert.assertFalse(foundGetParent, "Bundler.run must drop getParent call");
    }

    public void testPackRepositoryPlugin() {
        PackRepositoryPlugin plugin = new PackRepositoryPlugin();
        Assert.assertEquals("net/minecraft/server/packs/repository/PackRepository", plugin.getTargetClassName());

        byte[] original = createMockPackRepositoryClass();
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(plugin);

        byte[] transformed = orchestrator.transform(null, plugin.getTargetClassName(), null, null, original);
        Assert.assertNotNull(transformed);

        ClassModel model = classFile.parse(transformed);
        boolean foundInjectSources = false;
        for (MethodModel mm : model.methods()) {
            if (mm.methodName().stringValue().equals("<init>")) {
                for (java.lang.classfile.CodeElement ce : mm.code().orElseThrow()) {
                    if (ce instanceof InvokeInstruction inv
                            && inv.name().stringValue().equals("injectSources")) {
                        foundInjectSources = true;
                        break;
                    }
                }
            }
        }
        Assert.assertTrue(foundInjectSources, "PackRepository.<init> must contain injectSources injection");
    }
}
