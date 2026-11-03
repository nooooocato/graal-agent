package com.graalagent.bootstrap;

import com.graalagent.AgentMain;
import com.graalagent.diagnostics.DiagnosticsHub;
import com.graalagent.diagnostics.LogLevel;
import com.graalagent.test.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class AgentMainBootstrapTest {

    public void testAgentConfigAndDiagnosticsWireup() throws Exception {
        Path tempDir = Files.createTempDirectory("agentmain-bootstrap-test-");
        try {
            Path customScripts = tempDir.resolve("my-scripts");
            Path customLib = tempDir.resolve("my-lib");
            Path customLog = tempDir.resolve("logs/custom-agent.log");
            Files.createDirectories(customScripts);
            Files.createDirectories(customLib);

            String args = "scripts=" + customScripts.toString()
                    + ",lib=" + customLib.toString()
                    + ",log=" + customLog.toString()
                    + ",debug=true";

            AgentConfig config = AgentMain.startAgent("test-premain", args, null);

            Assert.assertNotNull(config, "startAgent should return non-null AgentConfig");
            Assert.assertEquals(customScripts.toAbsolutePath().normalize(), config.scriptsDir());
            Assert.assertEquals(customLib.toAbsolutePath().normalize(), config.libDir());
            Assert.assertEquals(customLog.toAbsolutePath().normalize(), config.logFilePath());
            Assert.assertTrue(config.debugMode(), "debugMode should be true");

            // DiagnosticsHub minimum level should be set to DEBUG
            Assert.assertEquals(LogLevel.DEBUG, DiagnosticsHub.getInstance().getMinLevel());

            // Check that customLog file was initialized
            Assert.assertTrue(Files.exists(customLog), "Custom log file should exist");
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public void testUncaughtExceptionHandlerDumpsAgentCrash() throws Exception {
        Path tempDumpDir = Files.createTempDirectory("agent-crash-dumps-");
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        try {
            AgentMain.setupUncaughtExceptionHandler(tempDumpDir.toFile());
            Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
            Assert.assertNotNull(handler, "UncaughtExceptionHandler should be set");

            // Create an exception originating from com.graalagent
            RuntimeException agentException = new RuntimeException("Simulated agent fatal failure");
            Thread testThread = new Thread(() -> {}, "test-worker-thread");

            handler.uncaughtException(testThread, agentException);

            // Verify a dump file was created in tempDumpDir
            File[] dumps = tempDumpDir.toFile().listFiles((dir, name) -> name.startsWith("agent-crash-") && name.endsWith(".dump"));
            Assert.assertNotNull(dumps, "Dumps array should not be null");
            Assert.assertTrue(dumps.length >= 1, "At least one crash dump file should be generated");

            String content = Files.readString(dumps[0].toPath());
            Assert.assertTrue(content.contains("Simulated agent fatal failure"), "Dump should contain exception message");
            Assert.assertTrue(content.contains("test-worker-thread"), "Dump should contain thread name");
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
            deleteRecursively(tempDumpDir);
        }
    }

    public void testUncaughtExceptionHandlerIgnoresNonAgentCrash() throws Exception {
        Path tempDumpDir = Files.createTempDirectory("agent-non-agent-dumps-");
        Thread.UncaughtExceptionHandler previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        try {
            AgentMain.setupUncaughtExceptionHandler(tempDumpDir.toFile());
            Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
            Assert.assertNotNull(handler, "UncaughtExceptionHandler should be set");

            // Create an exception with stack trace not containing com.graalagent
            Exception externalException = new Exception("External framework failure");
            externalException.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("org.example.ForeignClass", "run", "ForeignClass.java", 42)
            });

            Thread testThread = new Thread(() -> {}, "foreign-thread");
            handler.uncaughtException(testThread, externalException);

            // Verify NO dump file was created
            File[] dumps = tempDumpDir.toFile().listFiles((dir, name) -> name.startsWith("agent-crash-") && name.endsWith(".dump"));
            Assert.assertNotNull(dumps, "Dumps array should not be null");
            Assert.assertEquals(0, dumps.length);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
            deleteRecursively(tempDumpDir);
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
