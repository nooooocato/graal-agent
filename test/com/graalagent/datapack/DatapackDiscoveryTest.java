package com.graalagent.datapack;

import com.graalagent.test.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DatapackDiscoveryTest {

    public void testDatapackAutoDiscovery() throws IOException {
        Path tempDir = Files.createTempDirectory("graalagent-datapacks-test-");
        File scriptsDir = tempDir.toFile();

        // Initially empty
        List<Path> packs = DatapackManager.discoverPacks(scriptsDir);
        Assert.assertEquals(0, packs.size());

        // Create a valid pack with pack.mcmeta
        Path customPack = tempDir.resolve("datapacks/my_custom_pack");
        Files.createDirectories(customPack);
        Files.writeString(customPack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Test Pack\",\"pack_format\":61}}");

        // Create an invalid directory without pack.mcmeta
        Path invalidDir = tempDir.resolve("datapacks/not_a_pack");
        Files.createDirectories(invalidDir);

        packs = DatapackManager.discoverPacks(scriptsDir);
        Assert.assertEquals(1, packs.size());
        Assert.assertEquals("my_custom_pack", packs.get(0).getFileName().toString());
    }

    public void testSyntheticPackCreation() throws IOException {
        Path tempDir = Files.createTempDirectory("graalagent-synthetic-pack-");
        File scriptsDir = tempDir.toFile();

        Path packPath = DatapackManager.createSyntheticPack(scriptsDir, "auto_pack", "Auto generated pack", 61);
        Assert.assertTrue(Files.exists(packPath), "Pack directory should exist");
        Assert.assertTrue(Files.exists(packPath.resolve("pack.mcmeta")), "pack.mcmeta should exist");
        Assert.assertTrue(Files.exists(packPath.resolve("data")), "data directory should exist");

        List<Path> discovered = DatapackManager.discoverPacks(scriptsDir);
        Assert.assertEquals(1, discovered.size());
        Assert.assertEquals("auto_pack", discovered.get(0).getFileName().toString());
    }
}
