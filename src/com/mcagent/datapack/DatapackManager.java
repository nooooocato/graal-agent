package com.mcagent.datapack;

import com.mcagent.bridge.AgentBridge;
import com.mcagent.util.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DatapackManager {
    private static volatile File currentScriptsDir = null;

    public static void setScriptsDir(File scriptsDir) {
        currentScriptsDir = scriptsDir;
    }

    public static File getScriptsDir() {
        if (currentScriptsDir != null) {
            return currentScriptsDir;
        }
        return new File("scripts");
    }

    public static Path getDatapacksPath() {
        Path path = getScriptsDir().toPath().resolve("datapacks");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException ignored) {}
        return path;
    }

    public static List<Path> discoverPacks(File scriptsDir) {
        List<Path> results = new ArrayList<>();
        File dpDir = new File(scriptsDir, "datapacks");
        if (!dpDir.exists() || !dpDir.isDirectory()) {
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dpDir.toPath())) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && Files.exists(entry.resolve("pack.mcmeta"))) {
                    results.add(entry);
                } else if (entry.toString().toLowerCase().endsWith(".zip")) {
                    results.add(entry);
                }
            }
        } catch (IOException e) {
            Logger.warn("Failed to scan scripts datapacks directory: " + e.getMessage());
        }
        return results;
    }

    public static Path createSyntheticPack(File scriptsDir, String packId, String description, int packFormat) throws IOException {
        Path dpRoot = scriptsDir.toPath().resolve("datapacks");
        if (!Files.exists(dpRoot)) {
            Files.createDirectories(dpRoot);
        }

        Path packPath = dpRoot.resolve(packId);
        Files.createDirectories(packPath);
        Files.createDirectories(packPath.resolve("data"));

        String mcmeta = String.format("""
            {
              "pack": {
                "description": "%s",
                "pack_format": %d
              }
            }
            """, description != null ? description : "MC-JS-Agent Synthetic Pack", packFormat > 0 ? packFormat : 61);

        Files.writeString(packPath.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
        Logger.info("Created synthetic datapack at: " + packPath.toAbsolutePath());
        return packPath;
    }

    public static Object[] injectSources(Object[] originalSources) {
        if (originalSources == null) {
            return new Object[0];
        }

        try {
            Object sampleFolderSource = null;
            for (Object src : originalSources) {
                if (src != null && src.getClass().getName().endsWith("FolderRepositorySource")) {
                    sampleFolderSource = src;
                    break;
                }
            }

            if (sampleFolderSource != null) {
                Class<?> srcClass = sampleFolderSource.getClass();
                Field packTypeField = srcClass.getDeclaredField("packType");
                packTypeField.setAccessible(true);
                Object packType = packTypeField.get(sampleFolderSource);

                Field packSourceField = srcClass.getDeclaredField("packSource");
                packSourceField.setAccessible(true);
                Object packSource = packSourceField.get(sampleFolderSource);

                Field validatorField = srcClass.getDeclaredField("validator");
                validatorField.setAccessible(true);
                Object validator = validatorField.get(sampleFolderSource);

                Path datapacksPath = getDatapacksPath();

                Constructor<?> targetCtor = null;
                for (Constructor<?> c : srcClass.getConstructors()) {
                    if (c.getParameterCount() == 4) {
                        targetCtor = c;
                        break;
                    }
                }

                if (targetCtor != null) {
                    Object scriptsFolderSource = targetCtor.newInstance(datapacksPath, packType, packSource, validator);
                    Object[] augmented = Arrays.copyOf(originalSources, originalSources.length + 1);
                    augmented[originalSources.length] = scriptsFolderSource;
                    Logger.info("Successfully injected scripts/datapacks FolderRepositorySource into PackRepository: " + datapacksPath);
                    return augmented;
                }
            }
        } catch (Throwable t) {
            Logger.error("Failed to inject scripts/datapacks into PackRepository", t);
        }

        return originalSources;
    }

    public static void reloadServerResources() {
        Object server = AgentBridge.getServer();
        if (server == null) {
            Logger.info("No active MinecraftServer instance to reload resources.");
            return;
        }

        try {
            Method getPackRepo = server.getClass().getMethod("getPackRepository");
            Object packRepo = getPackRepo.invoke(server);

            Method reloadRepo = packRepo.getClass().getMethod("reload");
            reloadRepo.invoke(packRepo);

            Method getSelectedIds = packRepo.getClass().getMethod("getSelectedIds");
            Collection<?> selected = (Collection<?>) getSelectedIds.invoke(packRepo);

            Method reloadRes = server.getClass().getMethod("reloadResources", Collection.class);
            reloadRes.invoke(server, selected);
            Logger.info("Triggered server.reloadResources for " + selected.size() + " datapacks.");
        } catch (Throwable t) {
            Logger.error("Failed to invoke server.reloadResources", t);
        }
    }
}
