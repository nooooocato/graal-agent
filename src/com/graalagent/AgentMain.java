package com.graalagent;

import com.graalagent.script.ScriptManager;
import com.graalagent.transformer.MinecraftTransformer;
import com.graalagent.util.Logger;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class AgentMain {
    public static void premain(String agentArgs, Instrumentation inst) {
        startAgent("premain", agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        startAgent("agentmain", agentArgs, inst);
    }

    private static void startAgent(String mode, String agentArgs, Instrumentation inst) {
        try {
            // 1. Locate Agent directory
            File agentJar = getAgentJarFile();
            File agentDir = (agentJar != null && agentJar.getParentFile() != null)
                    ? agentJar.getParentFile().getCanonicalFile()
                    : new File(".").getCanonicalFile();

            // Initialize logger file relative to agent directory
            Logger.initLogFile(new File(agentDir, "agent.log"));

            Logger.info("==================================================");
            Logger.info("Minecraft 26.3 JavaScript Agent starting (" + mode + ")");
            Logger.info("Java Runtime: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
            Logger.info("Agent directory: " + agentDir.getAbsolutePath());
            Logger.info("Working directory: " + new File(".").getAbsolutePath());
            Logger.info("==================================================");

            // 2. Load dependent JARs (GraalJS, Truffle, ICU4J, etc.) into System ClassLoader
            loadLibraries(agentDir, agentArgs, inst);

            // 3. Determine Scripts directory
            File scriptsDir = resolveScriptsDir(agentArgs, agentDir);
            Logger.info("Scripts directory: " + scriptsDir.getAbsolutePath());

            // 4. Initialize ScriptManager
            ScriptManager.init(scriptsDir);

            // 5. Register Minecraft bytecode transformer
            inst.addTransformer(new MinecraftTransformer(), true);
            Logger.info("MinecraftTransformer registered with Instrumentation.");

            Logger.info("==================================================");
            Logger.info("Graal-JS-Agent initialization complete. Ready for MC!");
            Logger.info("==================================================");
        } catch (Throwable t) {
            Logger.error("Failed to start Graal-JS-Agent", t);
        }
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

        // 1. Check agentArgs for lib=
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

        // 2. Check agentDir/lib
        candidates.add(new File(agentDir, "lib"));

        // 3. Check ./lib (working directory)
        candidates.add(new File("lib"));

        // 4. Check parent directory ../lib
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

        // 1. Try agentDir/scripts
        File defaultScripts = new File(agentDir, "scripts");
        if (defaultScripts.exists()) {
            return defaultScripts;
        }

        // 2. Try ./scripts in working directory
        File cwdScripts = new File("scripts");
        if (cwdScripts.exists()) {
            return cwdScripts;
        }

        // Default back to agentDir/scripts
        return defaultScripts;
    }
}
