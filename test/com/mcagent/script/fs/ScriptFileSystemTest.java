package com.mcagent.script.fs;

import com.mcagent.test.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

public class ScriptFileSystemTest {
    private final Path tempScriptsDir;
    private final ScriptFileSystem fs;

    public ScriptFileSystemTest() {
        try {
            this.tempScriptsDir = Files.createTempDirectory("mcagent-scripts-test-");
            this.fs = new ScriptFileSystem(tempScriptsDir.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for tests", e);
        }
    }

    public void testPathTraversalOutsideScriptsForbidden() {
        // Simple .. traversal
        Assert.assertThrows(SecurityException.class, () -> fs.resolvePath("../forbidden.txt"));

        // Deep traversal
        Assert.assertThrows(SecurityException.class, () -> fs.resolvePath("../../windows/system32"));

        // Normalized sneaky traversal
        Assert.assertThrows(SecurityException.class, () -> fs.resolvePath("sub/../../escaped.txt"));

        // Absolute path outside scripts
        Assert.assertThrows(SecurityException.class, () -> fs.resolvePath("C:/Windows/System32/cmd.exe"));
    }

    public void testWriteAndReadUtf8String() {
        String filename = "data/greeting.txt";
        String content = "Hello Minecraft 26.3! 这是一个UTF-8测试文本 §a彩色字符 🚀";

        fs.writeFile(filename, content, "utf-8");
        Assert.assertTrue(fs.exists(filename), "File should exist after writing");

        String readBack = fs.readFile(filename, "utf-8");
        Assert.assertEquals(content, readBack);

        // Default encoding should be utf-8
        String defaultRead = fs.readFile(filename, null);
        Assert.assertEquals(content, defaultRead);
    }

    public void testWriteAndReadBytes() {
        String filename = "binary/test.bin";
        byte[] original = new byte[]{1, 2, 3, 0, -128, 127, 42, 99};

        fs.writeBytes(filename, original);
        Assert.assertTrue(fs.exists(filename), "Binary file should exist");

        byte[] readBack = fs.readBytes(filename);
        Assert.assertArrayEquals(original, readBack);
    }

    public void testMkdirAndIsDirectory() {
        String dirPath = "nested/deep/directory";
        Assert.assertFalse(fs.exists(dirPath), "Dir should not exist initially");

        boolean created = fs.mkdir(dirPath);
        Assert.assertTrue(created, "mkdir should succeed");
        Assert.assertTrue(fs.exists(dirPath), "Directory should exist");
        Assert.assertTrue(fs.isDirectory(dirPath), "isDirectory should be true");
    }

    public void testListFiles() {
        String dir = "listing_test";
        fs.mkdir(dir);
        fs.writeFile(dir + "/file1.txt", "1", "utf-8");
        fs.writeFile(dir + "/file2.txt", "2", "utf-8");
        fs.mkdir(dir + "/sub");

        String[] items = fs.list(dir);
        Assert.assertNotNull(items);
        Assert.assertEquals(3, items.length);

        Arrays.sort(items);
        Assert.assertEquals("file1.txt", items[0]);
        Assert.assertEquals("file2.txt", items[1]);
        Assert.assertEquals("sub", items[2]);
    }

    public void testDelete() {
        String filePath = "temp/to_delete.txt";
        fs.writeFile(filePath, "delete me", "utf-8");
        Assert.assertTrue(fs.exists(filePath), "File should exist before delete");

        boolean deleted = fs.delete(filePath);
        Assert.assertTrue(deleted, "Delete should return true");
        Assert.assertFalse(fs.exists(filePath), "File should not exist after delete");
    }

    public void testGraalJsFsIntegration() {
        org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                .build();
        context.getBindings("js").putMember("agentFs", fs);

        context.eval("js", "agentFs.writeFile('js_output.txt', 'Hello from GraalJS!', 'utf-8');");

        Assert.assertTrue(fs.exists("js_output.txt"), "File written via GraalJS should exist");
        Assert.assertEquals("Hello from GraalJS!", fs.readFile("js_output.txt", "utf-8"));
        context.close();
    }
}
