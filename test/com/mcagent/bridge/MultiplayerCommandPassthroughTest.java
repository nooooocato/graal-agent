package com.mcagent.bridge;

import com.mcagent.script.ScriptManager;
import com.mcagent.server.ServerCommandDispatcher;
import com.mcagent.test.Assert;
import com.mojang.brigadier.CommandDispatcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiplayerCommandPassthroughTest {

    public void testMultiplayerCommandPassthrough() throws Exception {
        Path tempDir = Files.createTempDirectory("mcagent-multiplayer-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        sm.evalInline("agent.registerCommand('serverinfo', function(args) {});");

        // Simulate client connected to multiplayer server
        AgentBridge.setMockMultiplayer(true);
        try {
            // Server-bound commands should NOT be swallowed (must return false to send packet)
            Assert.assertFalse(AgentBridge.onSendCommand("serverinfo"),
                    "Multiplayer /serverinfo should return false to pass through to server");
            Assert.assertFalse(AgentBridge.onSendCommand("js 1+1"),
                    "Multiplayer /js should return false to pass through to server");
            Assert.assertFalse(AgentBridge.onSendCommand("reloadjs"),
                    "Multiplayer /reloadjs should return false to pass through to server");

            // Client local debug commands should be intercepted locally (return true)
            Assert.assertTrue(AgentBridge.onSendCommand("cjs 1+1"),
                    "/cjs should be intercepted locally on client and return true");
            Assert.assertTrue(AgentBridge.onSendCommand("clientjs 1+1"),
                    "/clientjs should be intercepted locally on client and return true");
        } finally {
            AgentBridge.setMockMultiplayer(null);
        }
    }

    public void testMultiplayerSuggestionFilter() throws Exception {
        Path tempDir = Files.createTempDirectory("mcagent-multiplayer-sugg-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        sm.evalInline("agent.registerCommand('customservercmd', function(args) {});");

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();

        // In multiplayer, client must NOT inject custom script commands into client dispatcher
        // (they come purely from the server's network packet), but MUST inject /cjs
        AgentBridge.setMockMultiplayer(true);
        try {
            sm.onUpdateCommands(dispatcher);
            Assert.assertNotNull(dispatcher.getRoot().getChild("cjs"), "/cjs must be available in client suggestions");
            Assert.assertNull(dispatcher.getRoot().getChild("customservercmd"),
                    "Multiplayer client should not inject local custom commands; server drives suggestions");
        } finally {
            AgentBridge.setMockMultiplayer(null);
        }

        // In singleplayer/local mode, client can inject local commands
        CommandDispatcher<Object> spDispatcher = new CommandDispatcher<>();
        sm.onUpdateCommands(spDispatcher);
        Assert.assertNotNull(spDispatcher.getRoot().getChild("customservercmd"),
                "Singleplayer client should register custom commands");
    }

    public static class MockPlayerList {
        final List<Object> players = new ArrayList<>();
        public List<Object> getPlayers() {
            return players;
        }
    }

    public static class MockCommands {
        final List<Object> sentPlayers = new ArrayList<>();
        public void sendCommands(Object player) {
            sentPlayers.add(player);
        }
    }

    public static class MockMinecraftServer {
        final MockPlayerList playerList = new MockPlayerList();
        final MockCommands commands = new MockCommands();

        public MockPlayerList getPlayerList() {
            return playerList;
        }

        public MockCommands getCommands() {
            return commands;
        }
    }

    public void testServerSyncCommandsToPlayers() {
        MockMinecraftServer server = new MockMinecraftServer();
        Object p1 = new Object();
        Object p2 = new Object();
        server.playerList.players.add(p1);
        server.playerList.players.add(p2);

        ServerCommandDispatcher.syncCommandsToPlayers(server);

        Assert.assertEquals(2, server.commands.sentPlayers.size());
        Assert.assertEquals(p1, server.commands.sentPlayers.get(0));
        Assert.assertEquals(p2, server.commands.sentPlayers.get(1));
    }
}
