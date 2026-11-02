package com.graalagent.bridge;

import com.graalagent.test.Assert;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public class MethodHandleCacheTest {

    public static class SampleTarget {
        private String value = "initial";

        public String greet(String name) {
            return "Hello, " + name + " (" + value + ")";
        }

        public static int add(int a, int b) {
            return a + b;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public void testDynamicResolutionAndCacheHit() throws Throwable {
        MethodType greetType = MethodType.methodType(String.class, String.class);
        MethodHandle mh1 = MethodHandleCache.findVirtual(SampleTarget.class, "greet", greetType);
        MethodHandle mh2 = MethodHandleCache.findVirtual(SampleTarget.class, "greet", greetType);

        Assert.assertNotNull(mh1, "MethodHandle should not be null");
        Assert.assertEquals(mh1, mh2);

        SampleTarget target = new SampleTarget();
        String result = (String) mh1.invoke(target, "World");
        Assert.assertEquals("Hello, World (initial)", result);

        // Static method test
        MethodType addType = MethodType.methodType(int.class, int.class, int.class);
        MethodHandle staticMh1 = MethodHandleCache.findStatic(SampleTarget.class, "add", addType);
        MethodHandle staticMh2 = MethodHandleCache.findStatic(SampleTarget.class, "add", addType);
        Assert.assertEquals(staticMh1, staticMh2);

        int sum = (int) staticMh1.invokeExact(10, 25);
        Assert.assertEquals(35, sum);
    }

    public void testWarmup() {
        // Warmup with current classloader
        MethodHandleCache.warmup(getClass().getClassLoader());
        // Verify cache is not empty
        Assert.assertTrue(MethodHandleCache.size() > 0, "Cache should contain prewarmed handles");
    }

    public void testMissingMethodThrows() {
        Assert.assertThrows(RuntimeException.class, () -> {
            MethodHandleCache.findVirtual(SampleTarget.class, "nonExistentMethod", MethodType.methodType(void.class));
        });
    }

    public void testInvocationCorrectnessUnderLoop() throws Throwable {
        MethodHandle mh = MethodHandleCache.findVirtual(SampleTarget.class, "greet", MethodType.methodType(String.class, String.class));
        SampleTarget target = new SampleTarget();
        for (int i = 0; i < 1000; i++) {
            String res = (String) mh.invoke(target, "user" + i);
            Assert.assertEquals("Hello, user" + i + " (initial)", res);
        }
    }

    public void testGraalJsBenchmarkSimulation() {
        org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                .allowHostClassLookup(c -> true)
                .build();
        try {
            context.eval("js", """
                const MethodHandleCache = Java.type("com.graalagent.bridge.MethodHandleCache");
                const MethodType = Java.type("java.lang.invoke.MethodType");
                const StringClass = Java.type("java.lang.String").class;
                const ObjectClass = Java.type("java.lang.Object").class;

                const valueOfMethod = StringClass.getMethod("valueOf", ObjectClass);
                const reflectRes = valueOfMethod.invoke(null, "123");

                const mhType = MethodType.methodType(StringClass, ObjectClass);
                const mh = MethodHandleCache.findStatic(StringClass, "valueOf", mhType);
                const mhRes = mh.invokeWithArguments("123");
            """);
        } finally {
            context.close();
        }
    }
}
