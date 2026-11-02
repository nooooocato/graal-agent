package com.graalagent.bootstrap;

import com.graalagent.util.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Robust classloader library injector and deterministic dependency verifier.
 */
public class LibraryInjector {

    public static final List<String> DEFAULT_CRITICAL_CLASSES = List.of(
            "org.graalvm.polyglot.Context",
            "com.oracle.truffle.api.Truffle"
    );

    public static boolean verifyClassPresent(String className, ClassLoader cl) {
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try {
            Class.forName(className, false, cl);
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    public static List<String> checkDependencies(ClassLoader cl, List<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> missing = new ArrayList<>();
        for (String name : classNames) {
            if (!verifyClassPresent(name, cl)) {
                missing.add(name);
            }
        }
        return missing;
    }

    public static int injectLibraries(Path libDir, Instrumentation inst) {
        if (libDir == null || !Files.exists(libDir) || !Files.isDirectory(libDir)) {
            Logger.warn("Library directory does not exist: " + libDir);
            return 0;
        }

        int count = 0;
        try (var stream = Files.list(libDir)) {
            List<Path> jarFiles = stream
                    .filter(p -> p.toString().endsWith(".jar"))
                    .sorted()
                    .toList();

            for (Path jarPath : jarFiles) {
                try {
                    JarFile jarFile = new JarFile(jarPath.toFile());
                    if (inst != null) {
                        inst.appendToSystemClassLoaderSearch(jarFile);
                    }
                    count++;
                } catch (IOException e) {
                    Logger.error("Failed to append jar to system classloader search: " + jarPath, e);
                }
            }
        } catch (IOException e) {
            Logger.error("Failed to list library directory: " + libDir, e);
        }

        // Verify critical dependencies
        List<String> missing = checkDependencies(ClassLoader.getSystemClassLoader(), DEFAULT_CRITICAL_CLASSES);
        if (!missing.isEmpty()) {
            Logger.warn("Warning: Critical GraalVM dependencies could not be verified on system classloader: " + missing);
        }

        return count;
    }
}
