package com.graalagent.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class TestRunner {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("Running Antigravity Automated Unit Tests (Java 25)");
        System.out.println("==================================================");

        List<Class<?>> testClasses = new ArrayList<>();
        if (args.length > 0) {
            for (String arg : args) {
                try {
                    testClasses.add(Class.forName(arg));
                } catch (ClassNotFoundException e) {
                    System.err.println("Test class not found: " + arg);
                    System.exit(1);
                }
            }
        } else {
            // Default registered test suites
            addTestClassIfExists(testClasses, "com.graalagent.script.fs.ScriptFileSystemTest");
            addTestClassIfExists(testClasses, "com.graalagent.bridge.DualEnvironmentTest");
            addTestClassIfExists(testClasses, "com.graalagent.bridge.MultiplayerCommandPassthroughTest");
            addTestClassIfExists(testClasses, "com.graalagent.server.ServerCommandTest");
            addTestClassIfExists(testClasses, "com.graalagent.server.ServerSandboxingTest");
            addTestClassIfExists(testClasses, "com.graalagent.server.ReloadHierarchyTest");
            addTestClassIfExists(testClasses, "com.graalagent.datapack.DatapackDiscoveryTest");
            addTestClassIfExists(testClasses, "com.graalagent.diagnostics.DiagnosticsTest");
            addTestClassIfExists(testClasses, "com.graalagent.bridge.MethodHandleCacheTest");
            addTestClassIfExists(testClasses, "com.graalagent.bridge.brigadier.BrigadierAdapterTest");
            addTestClassIfExists(testClasses, "com.graalagent.bootstrap.BootstrapTest");
        }

        int total = 0;
        int passed = 0;
        int failed = 0;

        for (Class<?> clazz : testClasses) {
            System.out.println("\nRunning suite: " + clazz.getName());
            Object instance;
            try {
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                System.err.println("  [ERROR] Failed to instantiate " + clazz.getName() + ": " + t.getMessage());
                failed++;
                continue;
            }

            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().startsWith("test") && Modifier.isPublic(method.getModifiers())) {
                    total++;
                    try {
                        method.invoke(instance);
                        System.out.println("  [PASS] " + method.getName());
                        passed++;
                    } catch (Throwable t) {
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        System.err.println("  [FAIL] " + method.getName() + ": " + cause.getMessage());
                        cause.printStackTrace(System.err);
                        failed++;
                    }
                }
            }
        }

        System.out.println("\n--------------------------------------------------");
        System.out.printf("Test Summary: Total=%d, Passed=%d, Failed=%d%n", total, passed, failed);
        System.out.println("--------------------------------------------------");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void addTestClassIfExists(List<Class<?>> list, String className) {
        try {
            list.add(Class.forName(className));
        } catch (ClassNotFoundException ignored) {}
    }
}
