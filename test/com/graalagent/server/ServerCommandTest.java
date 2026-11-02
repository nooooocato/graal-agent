package com.graalagent.server;

import com.graalagent.script.ScriptManager;
import com.graalagent.test.Assert;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ServerCommandTest {

    public void testServerCommandRegistration() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        CommandNode<Object> jsNode = dispatcher.getRoot().getChild("js");
        Assert.assertNotNull(jsNode);
        Assert.assertEquals("js", jsNode.getName());

        CommandNode<Object> reloadNode = dispatcher.getRoot().getChild("reloadjs");
        Assert.assertNotNull(reloadNode);
        Assert.assertEquals("reloadjs", reloadNode.getName());
    }

    public void testPermissionPredicate() {
        Assert.assertTrue(ServerCommandDispatcher.checkPermission(null), "Console (null source/player) should always have permission");
    }

    public void testServerCommandExecution() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        Object mockSource = new Object();
        int result = dispatcher.execute("reloadjs", mockSource);
        Assert.assertEquals(1, result);
    }

    public void testZeroArgumentCustomCommandExecution() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-server-cmd-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        sm.evalInline("globalThis.serverinfoCalls = 0; globalThis.lastArgs = null; agent.registerCommand('serverinfo', function(args) { globalThis.serverinfoCalls++; globalThis.lastArgs = args; });");

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        Object mockSource = new Object();
        // 1. Zero-argument invocation: /serverinfo
        int resultZero = dispatcher.execute("serverinfo", mockSource);
        Assert.assertEquals(1, resultZero);
        Assert.assertEquals("1", sm.evalInline("String(globalThis.serverinfoCalls)"));
        Assert.assertEquals("", sm.evalInline("String(globalThis.lastArgs)"));

        // 2. Argument invocation: /serverinfo extra details
        int resultArgs = dispatcher.execute("serverinfo extra details", mockSource);
        Assert.assertEquals(1, resultArgs);
        Assert.assertEquals("2", sm.evalInline("String(globalThis.serverinfoCalls)"));
        Assert.assertEquals("extra details", sm.evalInline("String(globalThis.lastArgs)"));
    }

    public void testCustomCommandDynamicCallbackResolutionAcrossReload() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-reload-cmd-test-");
        File scriptFile = tempDir.resolve("01_test.js").toFile();
        Files.writeString(scriptFile.toPath(), "agent.registerCommand('fstest', function(args) { globalThis.fstestValue = 'v1:' + args; });");

        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        Object mockSource = new Object();
        int res1 = dispatcher.execute("fstest initial", mockSource);
        Assert.assertEquals(1, res1);
        Assert.assertEquals("v1:initial", sm.evalInline("globalThis.fstestValue"));

        // Update script and reload
        Files.writeString(scriptFile.toPath(), "agent.registerCommand('fstest', function(args) { globalThis.fstestValue = 'v2:' + args; });");
        sm.reload();

        // Dispatch command on previously registered dispatcher
        int res2 = dispatcher.execute("fstest reloaded", mockSource);
        Assert.assertEquals(1, res2);
        Assert.assertEquals("v2:reloaded", sm.evalInline("globalThis.fstestValue"));
    }

    public static class MockPlayer {
        public String getScoreboardName() {
            return "TestPlayer42";
        }
    }

    public static class MockPlayerSource {
        private final MockPlayer player = new MockPlayer();
        public MockPlayer getPlayer() {
            return player;
        }
    }

    public void testConsoleCommandAccessingNullPlayerThrowsError() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-player-err-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        sm.evalInline("agent.registerCommand('mypos', function(args) { if (player) { return 'has player'; } });");

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        // Console source has null player
        Object consoleSource = new Object();
        int res = dispatcher.execute("mypos", consoleSource);
        // Should return 0 due to missing player error reported
        Assert.assertEquals(0, res);
    }

    public void testPlayerCommandAccessingPlayerSucceeds() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-player-ok-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        sm.evalInline("globalThis.capturedPlayerName = null; agent.registerCommand('myname', function(args, p) { if (player) { globalThis.capturedPlayerName = player.getScoreboardName(); } });");

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        ServerCommandDispatcher.registerServerCommands(dispatcher);

        MockPlayerSource playerSource = new MockPlayerSource();
        int res = dispatcher.execute("myname", playerSource);
        Assert.assertEquals(1, res);
        Assert.assertEquals("TestPlayer42", sm.evalInline("globalThis.capturedPlayerName"));
    }
}
