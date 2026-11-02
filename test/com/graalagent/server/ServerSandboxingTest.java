package com.graalagent.server;

import com.graalagent.script.ScriptManager;
import com.graalagent.test.Assert;
import com.mojang.brigadier.CommandDispatcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ServerSandboxingTest {

    public static class MockPlayer {
        public String getScoreboardName() {
            return "TestPlayer42";
        }
    }

    public static class MockPlayerSource {
        private final MockPlayer player = new MockPlayer();
        private final List<String> feedback = new ArrayList<>();

        public MockPlayer getPlayer() {
            return player;
        }

        public void sendSuccess(Supplier<?> feedbackSupplier, boolean broadcastToOps) {
            Object comp = feedbackSupplier.get();
            feedback.add(comp != null ? comp.toString() : "");
        }

        public void sendFailure(Supplier<?> feedbackSupplier) {
            Object comp = feedbackSupplier.get();
            feedback.add(comp != null ? comp.toString() : "");
        }

        public List<String> getFeedback() {
            return feedback;
        }

        public String getLastFeedback() {
            return feedback.isEmpty() ? "" : feedback.get(feedback.size() - 1);
        }
    }

    public static class MockConsoleSource {
        private final List<String> feedback = new ArrayList<>();

        public Object getPlayer() {
            return null;
        }

        public void sendSuccess(Supplier<?> feedbackSupplier, boolean broadcastToOps) {
            Object comp = feedbackSupplier.get();
            feedback.add(comp != null ? comp.toString() : "");
        }

        public void sendFailure(Supplier<?> feedbackSupplier) {
            Object comp = feedbackSupplier.get();
            feedback.add(comp != null ? comp.toString() : "");
        }

        public List<String> getFeedback() {
            return feedback;
        }

        public String getLastFeedback() {
            return feedback.isEmpty() ? "" : feedback.get(feedback.size() - 1);
        }
    }

    public void testPlayerCannotAccessJavaType() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-sandbox-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        MockPlayerSource playerSource = new MockPlayerSource();
        int res = dispatcher.execute("js Java.type('java.lang.System')", playerSource);

        Assert.assertEquals(0, res);
        String lastFeedback = playerSource.getLastFeedback();
        Assert.assertTrue(lastFeedback.contains("安全") || lastFeedback.contains("SecurityException") || lastFeedback.contains("禁止"),
                "Feedback should indicate security restriction, but was: " + lastFeedback);
    }

    public void testPlayerCannotWriteFile() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-sandbox-fs-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        MockPlayerSource playerSource = new MockPlayerSource();
        int res = dispatcher.execute("js agent.fs.writeFile('evil.txt', 'malicious_content')", playerSource);

        Assert.assertEquals(0, res);
        File evilFile = tempDir.resolve("evil.txt").toFile();
        Assert.assertFalse(evilFile.exists(), "evil.txt should not have been created by player /js");

        String lastFeedback = playerSource.getLastFeedback();
        Assert.assertTrue(lastFeedback.contains("安全") || lastFeedback.contains("权限拒绝") || lastFeedback.contains("SecurityException"),
                "Feedback should indicate security error, but was: " + lastFeedback);
    }

    public void testConsoleHasFullAccess() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-sandbox-console-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        MockConsoleSource consoleSource = new MockConsoleSource();

        // 1. Console executing Java reflection
        int resJava = dispatcher.execute("js Java.type('java.lang.System').getProperty('java.version')", consoleSource);
        Assert.assertEquals(1, resJava);
        String lastFeedback = consoleSource.getLastFeedback();
        Assert.assertTrue(lastFeedback.contains("25"), "Console feedback should contain Java 25 version info: " + lastFeedback);

        // 2. Console executing file operations
        int resFs = dispatcher.execute("js agent.fs.writeFile('admin.txt', 'console_created')", consoleSource);
        Assert.assertEquals(1, resFs);
        File adminFile = tempDir.resolve("admin.txt").toFile();
        Assert.assertTrue(adminFile.exists(), "admin.txt should be created by console /js");
        String content = Files.readString(adminFile.toPath());
        Assert.assertEquals("console_created", content);
    }

    public void testPlayerCanExecuteSafeJs() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-sandbox-safe-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        MockPlayerSource playerSource = new MockPlayerSource();

        // Safe math computation
        int resMath = dispatcher.execute("js 100 + 200", playerSource);
        Assert.assertEquals(1, resMath);
        Assert.assertTrue(playerSource.getLastFeedback().contains("300"), "Feedback should contain 300: " + playerSource.getLastFeedback());

        // Safe player query
        int resPlayer = dispatcher.execute("js player.getScoreboardName()", playerSource);
        Assert.assertEquals(1, resPlayer);
        Assert.assertTrue(playerSource.getLastFeedback().contains("TestPlayer42"), "Feedback should contain TestPlayer42: " + playerSource.getLastFeedback());
    }
}
