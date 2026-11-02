package com.mcagent.script.fs;

import com.mcagent.util.Logger;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

public class ScriptFileSystem {
    private final Path baseCanonicalPath;

    public ScriptFileSystem(File baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("Base directory cannot be null");
        }
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            this.baseCanonicalPath = baseDir.toPath().toAbsolutePath().normalize();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to initialize ScriptFileSystem base path", t);
        }
    }

    public Path resolvePath(String relativePath) {
        if (relativePath == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }

        String sanitized = relativePath.replace('\\', '/').trim();
        Path rel = Paths.get(sanitized);

        if (rel.isAbsolute()) {
            throw new SecurityException("Absolute paths are forbidden in ScriptFileSystem: " + relativePath);
        }

        Path candidate = baseCanonicalPath.resolve(rel).normalize();

        if (!candidate.startsWith(baseCanonicalPath)) {
            throw new SecurityException("Path traversal attempt detected outside scripts root: " + relativePath);
        }

        return candidate;
    }

    public String readFile(String relativePath, String encoding) {
        Path target = resolvePath(relativePath);
        Charset charset = resolveCharset(encoding);
        try {
            return Files.readString(target, charset);
        } catch (IOException e) {
            Logger.error("Failed to read file: " + relativePath, e);
            throw new RuntimeException("Could not read file: " + relativePath, e);
        }
    }

    public byte[] readBytes(String relativePath) {
        Path target = resolvePath(relativePath);
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            Logger.error("Failed to read bytes: " + relativePath, e);
            throw new RuntimeException("Could not read bytes: " + relativePath, e);
        }
    }

    public void writeFile(String relativePath, String content, String encoding) {
        Path target = resolvePath(relativePath);
        Charset charset = resolveCharset(encoding);
        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(
                    target,
                    content != null ? content : "",
                    charset,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            Logger.error("Failed to write file: " + relativePath, e);
            throw new RuntimeException("Could not write file: " + relativePath, e);
        }
    }

    public void writeBytes(String relativePath, byte[] bytes) {
        Path target = resolvePath(relativePath);
        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            Files.write(
                    target,
                    bytes != null ? bytes : new byte[0],
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            Logger.error("Failed to write bytes: " + relativePath, e);
            throw new RuntimeException("Could not write bytes: " + relativePath, e);
        }
    }

    public void writeBytes(String relativePath, Object bytesObj) {
        if (bytesObj instanceof byte[] b) {
            writeBytes(relativePath, b);
        } else if (bytesObj instanceof Value val) {
            if (val.hasArrayElements()) {
                long size = val.getArraySize();
                byte[] bytes = new byte[(int) size];
                for (int i = 0; i < size; i++) {
                    bytes[i] = (byte) val.getArrayElement(i).asInt();
                }
                writeBytes(relativePath, bytes);
            } else {
                throw new IllegalArgumentException("Value is not an array");
            }
        } else {
            throw new IllegalArgumentException("Unsupported byte array type: " + (bytesObj != null ? bytesObj.getClass() : "null"));
        }
    }

    public boolean exists(String relativePath) {
        Path target = resolvePath(relativePath);
        return Files.exists(target);
    }

    public boolean isDirectory(String relativePath) {
        Path target = resolvePath(relativePath);
        return Files.isDirectory(target);
    }

    public boolean mkdir(String relativePath) {
        Path target = resolvePath(relativePath);
        try {
            Files.createDirectories(target);
            return true;
        } catch (IOException e) {
            Logger.error("Failed to create directory: " + relativePath, e);
            return false;
        }
    }

    public String[] list(String relativePath) {
        Path target = resolvePath(relativePath != null ? relativePath : "");
        if (!Files.exists(target) || !Files.isDirectory(target)) {
            return new String[0];
        }
        try (Stream<Path> stream = Files.list(target)) {
            return stream.map(p -> p.getFileName().toString()).toArray(String[]::new);
        } catch (IOException e) {
            Logger.error("Failed to list directory: " + relativePath, e);
            return new String[0];
        }
    }

    public boolean delete(String relativePath) {
        Path target = resolvePath(relativePath);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            Logger.error("Failed to delete path: " + relativePath, e);
            return false;
        }
    }

    public Path getBaseCanonicalPath() {
        return baseCanonicalPath;
    }

    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding.trim());
        } catch (Throwable t) {
            Logger.warn("Invalid charset '" + encoding + "', falling back to UTF-8");
            return StandardCharsets.UTF_8;
        }
    }
}
