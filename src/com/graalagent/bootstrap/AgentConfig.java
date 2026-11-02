package com.graalagent.bootstrap;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Strongly typed immutable agent bootstrap configuration.
 */
public record AgentConfig(
        Path agentJarPath,
        Path agentDir,
        Path scriptsDir,
        Path libDir,
        Path logFilePath,
        boolean debugMode
) {
    public static AgentConfig parse(String agentArgs, Path agentJarLocation) {
        Path baseDir;
        if (agentJarLocation != null && agentJarLocation.getParent() != null) {
            baseDir = agentJarLocation.getParent().toAbsolutePath().normalize();
        } else {
            baseDir = Paths.get(".").toAbsolutePath().normalize();
        }

        Map<String, String> params = parseArgs(agentArgs);

        Path scripts = resolvePath(params.get("scripts"), baseDir.resolve("scripts"), baseDir);
        Path lib = resolvePath(params.get("lib"), baseDir.resolve("lib"), baseDir);
        Path log = resolvePath(params.get("log"), baseDir.resolve("agent.log"), baseDir);
        boolean debug = Boolean.parseBoolean(params.getOrDefault("debug", "false"));

        return new AgentConfig(agentJarLocation, baseDir, scripts, lib, log, debug);
    }

    private static Map<String, String> parseArgs(String agentArgs) {
        Map<String, String> map = new HashMap<>();
        if (agentArgs == null || agentArgs.isBlank()) {
            return map;
        }
        for (String part : agentArgs.split(",")) {
            String clean = part.trim();
            if (clean.isEmpty()) continue;
            int eq = clean.indexOf('=');
            if (eq > 0) {
                String key = clean.substring(0, eq).trim().toLowerCase();
                String val = clean.substring(eq + 1).trim();
                map.put(key, val);
            } else {
                map.put(clean.toLowerCase(), "true");
            }
        }
        return map;
    }

    private static Path resolvePath(String customPath, Path defaultPath, Path baseDir) {
        if (customPath == null || customPath.isBlank()) {
            return defaultPath.toAbsolutePath().normalize();
        }
        Path p = Paths.get(customPath);
        if (!p.isAbsolute()) {
            p = baseDir.resolve(p);
        }
        return p.toAbsolutePath().normalize();
    }
}
