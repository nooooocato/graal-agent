package com.graalagent.transformer;

import com.graalagent.test.Assert;
import com.graalagent.transformer.smv.SMVProbeTarget;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.concurrent.atomic.AtomicInteger;

public class SMVOrchestratorTest {

    private byte[] getSampleClassBytes() throws Exception {
        String resourcePath = SMVProbeTarget.class.getName().replace('.', '/') + ".class";
        try (InputStream in = SMVProbeTarget.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Assert.assertNotNull(in, "SMVProbeTarget bytecode resource should exist");
            return in.readAllBytes();
        }
    }

    public void testPluginRegistrationAndExactClassNameRouting() throws Exception {
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        AtomicInteger pluginExecutionCount = new AtomicInteger(0);

        String targetName = "test/target/TargetClass";
        ClassTransformPlugin plugin = new ClassTransformPlugin() {
            @Override
            public String getTargetClassName() {
                return targetName;
            }

            @Override
            public void transform(ClassModel classModel, java.lang.classfile.ClassBuilder classBuilder) {
                pluginExecutionCount.incrementAndGet();
            }
        };

        orchestrator.registerPlugin(plugin);
        Assert.assertEquals(1, orchestrator.getPluginsFor(targetName).size());
        Assert.assertTrue(orchestrator.getPluginsFor("test/other/UnrelatedClass").isEmpty(), "Unrelated class should have no plugins");

        byte[] sampleBytes = getSampleClassBytes();

        // 1. Calling transform for unrelated class should return null (no change) and not trigger plugin
        byte[] unrelatedResult = orchestrator.transform(null, "test/other/UnrelatedClass", null, null, sampleBytes);
        Assert.assertNull(unrelatedResult, "Unrelated class transform should return null");
        Assert.assertEquals(0, pluginExecutionCount.get());

        // 2. Calling transform for target class should invoke the plugin
        byte[] targetResult = orchestrator.transform(null, targetName, null, null, sampleBytes);
        Assert.assertNotNull(targetResult, "Target class transform should return transformed bytes");
        Assert.assertEquals(1, pluginExecutionCount.get());
    }

    public void testMultiplePluginPipelineSequentialTransformation() throws Exception {
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        String targetName = "test/pipeline/TargetClass";

        // Plugin 1: Adds a new method "methodFromPlugin1"
        ClassTransformPlugin plugin1 = new ClassTransformPlugin() {
            @Override
            public String getTargetClassName() {
                return targetName;
            }

            @Override
            public void transform(ClassModel classModel, java.lang.classfile.ClassBuilder classBuilder) {
                classBuilder.withMethod(
                        "methodFromPlugin1",
                        MethodTypeDesc.ofDescriptor("()V"),
                        AccessFlag.PUBLIC.mask(),
                        mb -> mb.withCode(cb -> cb.return_())
                );
            }
        };

        // Plugin 2: Verifies methodFromPlugin1 exists in input ClassModel and adds "methodFromPlugin2"
        ClassTransformPlugin plugin2 = new ClassTransformPlugin() {
            @Override
            public String getTargetClassName() {
                return targetName;
            }

            @Override
            public void transform(ClassModel classModel, java.lang.classfile.ClassBuilder classBuilder) {
                boolean foundMethodFrom1 = false;
                for (MethodModel mm : classModel.methods()) {
                    if (mm.methodName().stringValue().equals("methodFromPlugin1")) {
                        foundMethodFrom1 = true;
                        break;
                    }
                }
                Assert.assertTrue(foundMethodFrom1, "Plugin 2 must receive bytecode produced by Plugin 1");

                classBuilder.withMethod(
                        "methodFromPlugin2",
                        MethodTypeDesc.ofDescriptor("()I"),
                        AccessFlag.PUBLIC.mask(),
                        mb -> mb.withCode(cb -> {
                            cb.iconst_1();
                            cb.ireturn();
                        })
                );
            }
        };

        orchestrator.registerPlugin(plugin1);
        orchestrator.registerPlugin(plugin2);

        byte[] sampleBytes = getSampleClassBytes();
        byte[] resultBytes = orchestrator.transform(null, targetName, null, null, sampleBytes);
        Assert.assertNotNull(resultBytes);

        ClassModel finalModel = ClassFile.of().parse(resultBytes);
        boolean foundMethod1 = false;
        boolean foundMethod2 = false;
        for (MethodModel mm : finalModel.methods()) {
            if (mm.methodName().stringValue().equals("methodFromPlugin1")) foundMethod1 = true;
            if (mm.methodName().stringValue().equals("methodFromPlugin2")) foundMethod2 = true;
        }
        Assert.assertTrue(foundMethod1, "Final class must contain method from Plugin 1");
        Assert.assertTrue(foundMethod2, "Final class must contain method from Plugin 2");
    }

    public void testInternalClassesIgnored() {
        SMVOrchestrator orchestrator = new SMVOrchestrator();
        orchestrator.registerPlugin(new ClassTransformPlugin() {
            @Override
            public String getTargetClassName() {
                return "java/lang/String";
            }

            @Override
            public void transform(ClassModel classModel, java.lang.classfile.ClassBuilder classBuilder) {
                throw new AssertionError("Should never be called for JDK classes");
            }
        });

        byte[] dummy = new byte[]{1, 2, 3};
        Assert.assertNull(orchestrator.transform(null, "java/lang/String", null, null, dummy));
        Assert.assertNull(orchestrator.transform(null, "jdk/internal/Foo", null, null, dummy));
        Assert.assertNull(orchestrator.transform(null, "sun/misc/Unsafe", null, null, dummy));
        Assert.assertNull(orchestrator.transform(null, "com/oracle/truffle/Truffle", null, null, dummy));
        Assert.assertNull(orchestrator.transform(null, "com/graalagent/bridge/AgentBridge", null, null, dummy));
    }

    public void testCreateDefaultRegistersAllPlugins() {
        SMVOrchestrator orchestrator = SMVOrchestrator.createDefault();
        Assert.assertEquals(1, orchestrator.getPluginsFor("net/minecraft/client/Minecraft").size());
        Assert.assertEquals(1, orchestrator.getPluginsFor("net/minecraft/client/KeyboardHandler").size());
        Assert.assertEquals(1, orchestrator.getPluginsFor("net/minecraft/client/multiplayer/ClientPacketListener").size());
        Assert.assertEquals(2, orchestrator.getPluginsFor("net/minecraft/server/MinecraftServer").size());
        Assert.assertEquals(1, orchestrator.getPluginsFor("net/minecraft/commands/Commands").size());
        Assert.assertEquals(1, orchestrator.getPluginsFor("net/minecraft/bundler/Main").size());
        Assert.assertEquals(1, orchestrator.getPluginsFor("net/minecraft/server/packs/repository/PackRepository").size());
    }
}
