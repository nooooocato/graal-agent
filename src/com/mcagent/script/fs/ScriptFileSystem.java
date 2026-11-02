package com.mcagent.script.fs;

import java.io.File;
import java.nio.file.Path;

public class ScriptFileSystem {
    private final File baseDir;

    public ScriptFileSystem(File baseDir) {
        this.baseDir = baseDir;
    }

    public Path resolvePath(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public String readFile(String relativePath, String encoding) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public byte[] readBytes(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void writeFile(String relativePath, String content, String encoding) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void writeBytes(String relativePath, byte[] bytes) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean exists(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean isDirectory(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean mkdir(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public String[] list(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean delete(String relativePath) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
