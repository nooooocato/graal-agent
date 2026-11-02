package com.mcagent;

import com.mcagent.script.ScriptManager;
import com.mcagent.transformer.MinecraftTransformer;
import com.mcagent.util.Logger;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

public class AgentMain {
    public static void premain(String agentArgs, Instrumentation inst) {
        startAgent("premain", agentArgs, inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        startAgent("agentmain", agentArgs, inst);
    }

    private static void startAgent(String mode, String agentArgs, Instrumentation inst) {
        Logger.info("==================================================");
        Logger.info("Minecraft 26.3 JavaScript Agent starting (" + mode + ")");
        Logger.info("Java Runtime: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        Logger.info("==================================================");

        try {
            // 1. Locate Agent directory
            File agentJar = getAgentJarFile();
            File agentDir = (agentJar != null && agentJar.getParentFile() != null)
                    ? agentJar.getParentFile()
                    : new File(".");

            Logger.info("Agent directory: " + agentDir.getAbsolutePath());

            // 2. Load dependent JARs (GraalJS, Truffle, ICU4J, etc.) into System ClassLoader
            loadLibraries(agentDir, inst);

            // 3. Determine Scripts directory
            File scriptsDir = resolveScriptsDir(agentArgs, agentDir);
            Logger.info("Scripts directory: " + scriptsDir.getAbsolutePath());

            // 4. Initialize ScriptManager
            ScriptManager.init(scriptsDir);

            // 5. Register Minecraft bytecode transformer
            inst.addTransformer(new MinecraftTransformer(), true);
            Logger.info("MinecraftTransformer registered with Instrumentation.");

            Logger.info("==================================================");
            Logger.info("MC-JS-Agent initialization complete. Ready for MC!");
            Logger.info("==================================================");
        } catch (Throwable t) {
            Logger.error("Failed to start MC-JS-Agent", t);
        }
    }

    private static File getAgentJarFile() {
        try {
            String path = AgentMain.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            File f = new File(decoded);
            if (f.exists()) {
                return f;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void loadLibraries(File agentDir, Instrumentation inst) {
        File libDir = new File(agentDir, "lib");
        if (!libDir.exists() || !libDir.isDirectory()) {
            Logger.warn("No 'lib' directory found at " + libDir.getAbsolutePath() + "; skipping jar appending.");
            return;
        }

        File[] jars = libDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            Logger.warn("No JAR files found in " + libDir.getAbsolutePath());
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
            String clean = agentArgs.trim();
            if (clean.startsWith("scripts=")) {
                clean = clean.substring("scripts=".length());
            }
            File custom = new File(clean);
            if (!custom.isAbsolute()) {
                custom = new File(agentDir, clean);
            }
            return custom;
        }

        // Default: agentDir/scripts
        return new File(agentDir, "scripts");
    }
}
