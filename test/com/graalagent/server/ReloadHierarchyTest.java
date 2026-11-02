package com.graalagent.server;

import com.graalagent.bridge.AgentBridge;
import com.graalagent.script.ScriptManager;
import com.graalagent.test.Assert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReloadHierarchyTest {

    public static class MockPackRepository {
        public void reload() {}
        public Collection<String> getSelectedIds() {
            return List.of("vanilla");
        }
    }

    public static class MockServer {
        public boolean reloadResourcesCalled = false;
        private final MockPackRepository packRepo = new MockPackRepository();

        public MockPackRepository getPackRepository() {
            return packRepo;
        }

        public CompletableFuture<Void> reloadResources(Collection<String> packs) {
            reloadResourcesCalled = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    public void testReloadJsDoesNotTriggerDatapackReload() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-reloadjs-test-");
        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        MockServer mockServer = new MockServer();
        AgentBridge.setServer(mockServer);

        try {
            sm.reload();
            Assert.assertFalse(mockServer.reloadResourcesCalled,
                    "/reloadjs should lightly reload only JS scripts and NOT trigger server datapack reload (reloadResources)");
        } finally {
            AgentBridge.setServer(null);
            sm.stop();
        }
    }

    public void testServerReloadResourcesTriggersJsReload() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-vanilla-reload-test-");
        File scriptFile = tempDir.resolve("01_test.js").toFile();
        Files.writeString(scriptFile.toPath(), "globalThis.reloadMarker = 1;");

        ScriptManager sm = new ScriptManager(tempDir.toFile());
        sm.start();

        Assert.assertEquals("1", sm.evalInline("String(globalThis.reloadMarker)"));

        // Change script content on disk
        Files.writeString(scriptFile.toPath(), "globalThis.reloadMarker = 2;");

        CompletableFuture<Void> serverReloadFuture = new CompletableFuture<>();
        CompletableFuture<Void> chainedFuture = AgentBridge.onServerReloadResources(serverReloadFuture);

        Assert.assertNotNull(chainedFuture);
        serverReloadFuture.complete(null);
        chainedFuture.join();

        try {
            Assert.assertEquals("2", sm.evalInline("String(globalThis.reloadMarker)"));
        } finally {
            sm.stop();
        }
    }
}
