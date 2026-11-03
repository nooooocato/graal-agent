package com.graalagent;

import com.graalagent.bootstrap.AgentConfig;
import com.graalagent.bootstrap.LibraryInjector;
import com.graalagent.bridge.MethodHandleCache;
import com.graalagent.diagnostics.CrashDumpRecorder;
import com.graalagent.diagnostics.DiagnosticsHub;
import com.graalagent.diagnostics.LogLevel;
import com.graalagent.script.ScriptManager;
import com.graalagent.transformer.SMVOrchestrator;
import com.graalagent.util.Logger;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

public class AgentMain {
    public static void premain(String agentArgs, Instrumentation inst) {
        startAgent("premain", agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        startAgent("agentmain", agentArgs, inst);
    }

    public static AgentConfig startAgent(String mode, String agentArgs, Instrumentation inst) {
        File agentJar = getAgentJarFile();
        Path agentJarPath = agentJar != null ? agentJar.toPath() : null;
        AgentConfig config = null;

        try {
            config = AgentConfig.parse(agentArgs, agentJarPath);

            // Configure diagnostics hub
            DiagnosticsHub hub = DiagnosticsHub.getInstance();
            hub.initFile(config.logFilePath().toFile());
            if (config.debugMode()) {
                hub.setMinLevel(LogLevel.DEBUG);
            }

            // Register global uncaught exception crash recorder
            setupUncaughtExceptionHandler();

            Logger.info("==================================================");
            Logger.info("Minecraft 26.3 JavaScript Agent starting (" + mode + ")");
            Logger.info("Java Runtime: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
            Logger.info("Agent directory: " + config.agentDir().toAbsolutePath());
            Logger.info("Working directory: " + Path.of(".").toAbsolutePath().normalize());
            Logger.info("Scripts directory: " + config.scriptsDir().toAbsolutePath());
            Logger.info("Library directory: " + config.libDir().toAbsolutePath());
            Logger.info("Log file: " + config.logFilePath().toAbsolutePath());
            Logger.info("Debug mode: " + config.debugMode());
            Logger.info("==================================================");

            // Inject libraries into system classloader search
            int loadedJars = LibraryInjector.injectLibraries(config.libDir(), inst);
            Logger.info("Injected " + loadedJars + " library JAR(s) into system classloader search.");

            // Initialize ScriptManager
            ScriptManager.init(config.scriptsDir().toFile());

            // Register SMV bytecode mesh orchestrator & micro-plugins
            if (inst != null) {
                SMVOrchestrator orchestrator = SMVOrchestrator.createDefault();
                inst.addTransformer(orchestrator, true);
                Logger.info("SMVOrchestrator registered with Instrumentation (8 micro-plugins active).");
            }

            // Static warmup of MethodHandleCache
            MethodHandleCache.warmup();

            Logger.info("==================================================");
            Logger.info("Graal-JS-Agent initialization complete. Ready for MC!");
            Logger.info("==================================================");
            return config;
        } catch (Throwable t) {
            Logger.error("Failed to start Graal-JS-Agent (" + mode + ")", t);
            try {
                new CrashDumpRecorder().recordCrash(
                        t,
                        "Agent " + mode + " Startup Failure",
                        Map.of(
                                "mode", mode,
                                "agentArgs", agentArgs != null ? agentArgs : "null",
                                "agentDir", config != null ? config.agentDir().toString() : "unknown"
                        )
                );
            } catch (Throwable dumpErr) {
                Logger.error("Failed to record startup crash dump", dumpErr);
            }
            return config;
        }
    }

    public static void setupUncaughtExceptionHandler() {
        setupUncaughtExceptionHandler(new File("dumps"));
    }

    public static void setupUncaughtExceptionHandler(File dumpDir) {
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (isAgentException(throwable)) {
                try {
                    new CrashDumpRecorder(dumpDir).recordCrash(
                            throwable,
                            "Uncaught Exception in thread: " + thread.getName(),
                            Map.of(
                                    "threadName", thread.getName(),
                                    "threadId", thread.threadId()
                            )
                    );
                } catch (Throwable ignored) {}
            }
            if (prev != null) {
                prev.uncaughtException(thread, throwable);
            }
        });
    }

    private static boolean isAgentException(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            for (StackTraceElement elem : curr.getStackTrace()) {
                if (elem.getClassName().startsWith("com.graalagent.")) {
                    return true;
                }
            }
            curr = curr.getCause();
        }
        return false;
    }

    private static File getAgentJarFile() {
        try {
            String path = AgentMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            File f = new File(decoded);
            if (f.exists()) {
                return f.getCanonicalFile();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void loadLibraries(File agentDir, String agentArgs, Instrumentation inst) {
        List<File> candidates = new ArrayList<>();

        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            for (String part : agentArgs.split(",")) {
                String clean = part.trim();
                if (clean.startsWith("lib=")) {
                    String libPath = clean.substring("lib=".length());
                    File customLib = new File(libPath);
                    if (!customLib.isAbsolute()) {
                        customLib = new File(agentDir, libPath);
                    }
                    candidates.add(customLib);
                }
            }
        }

        candidates.add(new File(agentDir, "lib"));
        candidates.add(new File("lib"));
        if (agentDir.getParentFile() != null) {
            candidates.add(new File(agentDir.getParentFile(), "lib"));
        }

        File foundLibDir = null;
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                foundLibDir = candidate;
                break;
            }
        }

        if (foundLibDir == null) {
            Logger.error("CRITICAL: No 'lib' directory found! Checked locations:");
            for (File c : candidates) {
                Logger.error("  - " + c.getAbsolutePath());
            }
            Logger.error("Please ensure the 'lib' folder containing GraalJS JARs is placed alongside graal-agent.jar!");
            return;
        }

        Logger.info("Loading GraalJS dependencies from: " + foundLibDir.getAbsolutePath());
        File[] jars = foundLibDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            Logger.warn("No JAR files found in " + foundLibDir.getAbsolutePath());
            return;
        }

        for (File jar : jars) {
            try {
                Logger.info("Appending dependency to system classloader: " + jar.getName());
                inst.appendToSystemClassLoaderSearch(new JarFile(jar));
            } catch (Throwable t) {
                Logger.error("Failed to append jar: " + jar.getAbsolutePath(), t);
            }
        }
    }

    private static File resolveScriptsDir(String agentArgs, File agentDir) {
        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            for (String part : agentArgs.split(",")) {
                String clean = part.trim();
                if (clean.startsWith("scripts=")) {
                    clean = clean.substring("scripts=".length());
                    File custom = new File(clean);
                    if (!custom.isAbsolute()) {
                        custom = new File(agentDir, clean);
                    }
                    return custom;
                }
            }
        }

        File defaultScripts = new File(agentDir, "scripts");
        if (defaultScripts.exists()) {
            return defaultScripts;
        }

        File cwdScripts = new File("scripts");
        if (cwdScripts.exists()) {
            return cwdScripts;
        }

        return defaultScripts;
    }
}
