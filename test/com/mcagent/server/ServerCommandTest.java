package com.mcagent.server;

import com.mcagent.test.Assert;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

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
}
