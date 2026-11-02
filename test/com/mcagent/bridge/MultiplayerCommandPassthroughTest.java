package com.mcagent.bridge;

import com.mcagent.script.ScriptManager;
import com.mcagent.test.Assert;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
