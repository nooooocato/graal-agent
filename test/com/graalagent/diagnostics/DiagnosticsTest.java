package com.graalagent.diagnostics;

import com.graalagent.test.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DiagnosticsTest {

    public void testLogLevelFiltering() {
        DiagnosticsHub hub = new DiagnosticsHub();
        hub.setMinLevel(LogLevel.WARN);
        
        Assert.assertFalse(hub.isLoggable(LogLevel.DEBUG), "DEBUG should not be loggable when min level is WARN");
        Assert.assertFalse(hub.isLoggable(LogLevel.INFO), "INFO should not be loggable when min level is WARN");
        Assert.assertTrue(hub.isLoggable(LogLevel.WARN), "WARN should be loggable when min level is WARN");
        Assert.assertTrue(hub.isLoggable(LogLevel.ERROR), "ERROR should be loggable when min level is WARN");
        Assert.assertTrue(hub.isLoggable(LogLevel.AUDIT), "AUDIT should be loggable when min level is WARN");
    }

    public void testAnsiColorFormatting() {
        DiagnosticsHub hub = new DiagnosticsHub();
        LocalTime testTime = LocalTime.of(12, 34, 56);
        
        String infoLog = hub.formatConsole(LogLevel.INFO, "System started", testTime);
        Assert.assertTrue(infoLog.contains("\u001B[32m"), "INFO log should contain green ANSI code");
        Assert.assertTrue(infoLog.contains("System started"), "Formatted log should contain original message");
        Assert.assertTrue(infoLog.contains("12:34:56"), "Formatted log should contain timestamp");
        Assert.assertTrue(infoLog.contains("\u001B[0m"), "Formatted log should contain ANSI reset code");

        String warnLog = hub.formatConsole(LogLevel.WARN, "Resource low", testTime);
        Assert.assertTrue(warnLog.contains("\u001B[33m"), "WARN log should contain yellow ANSI code");

        String errorLog = hub.formatConsole(LogLevel.ERROR, "Null pointer", testTime);
        Assert.assertTrue(errorLog.contains("\u001B[31m"), "ERROR log should contain red ANSI code");

        String auditLog = hub.formatConsole(LogLevel.AUDIT, "Security check", testTime);
        Assert.assertTrue(auditLog.contains("\u001B[35m"), "AUDIT log should contain magenta/purple ANSI code");
    }

    public void testAsyncFileLogging() throws Exception {
        File tempFile = File.createTempFile("diagnostics-test-", ".log");
        tempFile.deleteOnExit();

        DiagnosticsHub hub = new DiagnosticsHub();
        hub.initFile(tempFile);
        hub.setMinLevel(LogLevel.DEBUG);

        hub.info("Diagnostic message 1 - UTF-8: 测试中文日志");
        hub.warn("Diagnostic message 2 - warning");
        hub.error("Diagnostic message 3 - error");
        
        // Wait for async queue to flush
        hub.flush();
        hub.shutdown();

        List<String> lines = Files.readAllLines(tempFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(lines.size() >= 3, "Log file should contain at least 3 lines");
        Assert.assertTrue(lines.get(0).contains("INFO: Diagnostic message 1 - UTF-8: 测试中文日志"), "Line 1 should match");
        Assert.assertTrue(lines.get(1).contains("WARN: Diagnostic message 2 - warning"), "Line 2 should match");
        Assert.assertTrue(lines.get(2).contains("ERROR: Diagnostic message 3 - error"), "Line 3 should match");
    }

    public void testCrashDumpRecorderGeneration() throws Exception {
        File tempDir = Files.createTempDirectory("crash-dump-test-").toFile();
        tempDir.deleteOnExit();

        CrashDumpRecorder recorder = new CrashDumpRecorder(tempDir);
        RuntimeException testEx = new RuntimeException("Simulated bytecode fault");
        Map<String, Object> state = new HashMap<>();
        state.put("Active Modlets", "[magic_system (v1.0.0)]");
        state.put("Context State", "READY");

        File dumpFile = recorder.recordCrash(testEx, "ClassLoader: net.minecraft.client.main.Main", state);
        Assert.assertNotNull(dumpFile, "Dump file must not be null");
        Assert.assertTrue(dumpFile.exists(), "Dump file should exist on disk");
        Assert.assertTrue(dumpFile.getName().startsWith("agent-crash-"), "Dump file name prefix mismatch");
        Assert.assertTrue(dumpFile.getName().endsWith(".dump"), "Dump file name suffix mismatch");

        String content = Files.readString(dumpFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(content.contains("GRAAL-AGENT CRASH DUMP REPORT"), "Report header missing");
        Assert.assertTrue(content.contains("Runtime Environment:"), "Runtime Environment section missing");
        Assert.assertTrue(content.contains("Java Version:"), "Java Version missing");
        Assert.assertTrue(content.contains("Simulated bytecode fault"), "Exception message missing");
        Assert.assertTrue(content.contains("magic_system (v1.0.0)"), "State dump missing");
    }
}
