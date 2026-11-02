package com.graalagent.transformer.smv;

import com.graalagent.test.Assert;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MethodVirtualizerTest {

    public static class SampleTarget {
        public static int hookCounter = 0;

        public String greet(String name) {
            return "Original: " + name;
        }

        public static int add(int a, int b) {
            return a + b;
        }
    }

    public static void onHook() {
        SampleTarget.hookCounter++;
    }

    private byte[] getSampleClassBytes() throws Exception {
        String resourcePath = SampleTarget.class.getName().replace('.', '/') + ".class";
        try (InputStream in = SampleTarget.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Assert.assertNotNull(in, "SampleTarget bytecode resource should exist");
            return in.readAllBytes();
        }
    }

    public void testShadowMethodGenerationAndDispatcherForwarding() throws Exception {
        byte[] originalBytes = getSampleClassBytes();
        ClassFile cf = ClassFile.of();
        ClassModel cm = cf.parse(originalBytes);

        byte[] transformedBytes = cf.transformClass(cm, (cb, ce) -> {
            if (ce instanceof MethodModel mm && mm.methodName().stringValue().equals("greet")) {
                MethodVirtualizer.virtualize(cb, mm, "$$shadow$greet", codeBuilder -> {
                    // Dispatcher logic: invoke onHook() then invoke shadow method
                    codeBuilder.invokestatic(
                            ClassDesc.of(MethodVirtualizerTest.class.getName()),
                            "onHook",
                            MethodTypeDesc.ofDescriptor("()V")
                    );
                    MethodVirtualizer.invokeShadow(
                            codeBuilder,
                            ClassDesc.of(SampleTarget.class.getName()),
                            "$$shadow$greet",
                            mm.methodTypeSymbol(),
                            false
                    );
                });
                return;
            }
            cb.with(ce);
        });

        // 1. Verify transformed class structure
        ClassModel transformedModel = cf.parse(transformedBytes);
        boolean foundShadow = false;
        boolean foundOriginal = false;

        for (MethodModel mm : transformedModel.methods()) {
            String name = mm.methodName().stringValue();
            if (name.equals("$$shadow$greet")) {
                foundShadow = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow method must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow method must be synthetic");
                Assert.assertFalse(mm.flags().has(AccessFlag.PUBLIC), "Shadow method must not be public");
            } else if (name.equals("greet")) {
                foundOriginal = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.PUBLIC), "Original method must remain public");
            }
        }

        Assert.assertTrue(foundShadow, "Transformed class must contain $$shadow$greet");
        Assert.assertTrue(foundOriginal, "Transformed class must contain original greet dispatcher");

        // 2. Load class and verify runtime behavior
        DynamicClassLoader loader = new DynamicClassLoader();
        Class<?> dynamicClass = loader.defineClass(SampleTarget.class.getName(), transformedBytes);
        Object instance = dynamicClass.getDeclaredConstructor().newInstance();

        SampleTarget.hookCounter = 0;
        Method greetMethod = dynamicClass.getMethod("greet", String.class);
        Object result = greetMethod.invoke(instance, "Alice");

        Assert.assertEquals(1, SampleTarget.hookCounter);
        Assert.assertEquals("Original: Alice", result);
    }

    public void testStaticMethodVirtualization() throws Exception {
        byte[] originalBytes = getSampleClassBytes();
        ClassFile cf = ClassFile.of();
        ClassModel cm = cf.parse(originalBytes);

        byte[] transformedBytes = cf.transformClass(cm, (cb, ce) -> {
            if (ce instanceof MethodModel mm && mm.methodName().stringValue().equals("add")) {
                MethodVirtualizer.virtualize(cb, mm, "$$shadow$add", codeBuilder -> {
                    // Dispatcher logic: directly invoke shadow method
                    MethodVirtualizer.invokeShadow(
                            codeBuilder,
                            ClassDesc.of(SampleTarget.class.getName()),
                            "$$shadow$add",
                            mm.methodTypeSymbol(),
                            true
                    );
                });
                return;
            }
            cb.with(ce);
        });

        ClassModel transformedModel = cf.parse(transformedBytes);
        boolean foundShadow = false;
        for (MethodModel mm : transformedModel.methods()) {
            if (mm.methodName().stringValue().equals("$$shadow$add")) {
                foundShadow = true;
                Assert.assertTrue(mm.flags().has(AccessFlag.STATIC), "Shadow static method must remain static");
                Assert.assertTrue(mm.flags().has(AccessFlag.PRIVATE), "Shadow static method must be private");
                Assert.assertTrue(mm.flags().has(AccessFlag.SYNTHETIC), "Shadow static method must be synthetic");
            }
        }
        Assert.assertTrue(foundShadow, "Transformed class must contain $$shadow$add");

        DynamicClassLoader loader = new DynamicClassLoader();
        Class<?> dynamicClass = loader.defineClass(SampleTarget.class.getName(), transformedBytes);
        Method addMethod = dynamicClass.getMethod("add", int.class, int.class);
        Object result = addMethod.invoke(null, 15, 27);
        Assert.assertEquals(42, result);
    }

    private static class DynamicClassLoader extends ClassLoader {
        public DynamicClassLoader() {
            super(MethodVirtualizerTest.class.getClassLoader());
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }
}
