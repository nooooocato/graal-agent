package com.graalagent.bootstrap;

import com.graalagent.test.Assert;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BootstrapTest {

    public void testDefaultConfigResolution() throws Exception {
        Path tempBase = Files.createTempDirectory("agent-config-test-");
        Path agentJar = tempBase.resolve("graal-agent.jar");

        AgentConfig config = AgentConfig.parse(null, agentJar);
        Assert.assertNotNull(config, "Config should not be null");
        Assert.assertEquals(tempBase.toAbsolutePath().normalize(), config.agentDir());
        Assert.assertEquals(tempBase.resolve("scripts").toAbsolutePath().normalize(), config.scriptsDir());
        Assert.assertEquals(tempBase.resolve("lib").toAbsolutePath().normalize(), config.libDir());
        Assert.assertEquals(tempBase.resolve("agent.log").toAbsolutePath().normalize(), config.logFilePath());
        Assert.assertFalse(config.debugMode(), "Debug mode should default to false");
    }

    public void testCommandLineArgsParsing() throws Exception {
        Path tempBase = Files.createTempDirectory("agent-config-args-");
        Path agentJar = tempBase.resolve("graal-agent.jar");

        String args = "scripts=custom_scripts,lib=custom_lib,log=custom_agent.log,debug=true";
        AgentConfig config = AgentConfig.parse(args, agentJar);

        Assert.assertTrue(config.scriptsDir().endsWith("custom_scripts"), "scriptsDir should end with custom_scripts");
        Assert.assertTrue(config.libDir().endsWith("custom_lib"), "libDir should end with custom_lib");
        Assert.assertTrue(config.logFilePath().endsWith("custom_agent.log"), "logFilePath should end with custom_agent.log");
        Assert.assertTrue(config.debugMode(), "debugMode should be true");
    }

    public void testDeterministicDependencyVerification() {
        // Critical class: org.graalvm.polyglot.Context must exist in this runtime
        boolean verified = LibraryInjector.verifyClassPresent("org.graalvm.polyglot.Context", getClass().getClassLoader());
        Assert.assertTrue(verified, "org.graalvm.polyglot.Context should be present");

        boolean missing = LibraryInjector.verifyClassPresent("com.graalagent.fake.NonExistentClass", getClass().getClassLoader());
        Assert.assertFalse(missing, "NonExistentClass should not be present");

        // Verify standard dependency check passes
        List<String> missingClasses = LibraryInjector.checkDependencies(getClass().getClassLoader(), List.of(
                "org.graalvm.polyglot.Context",
                "com.oracle.truffle.api.Truffle"
        ));
        Assert.assertTrue(missingClasses.isEmpty(), "Critical GraalVM dependencies should not be missing in test env");
    }

    public void testMissingDependencyDetection() {
        List<String> missing = LibraryInjector.checkDependencies(getClass().getClassLoader(), List.of(
                "org.graalvm.polyglot.Context",
                "non.existent.TruffleComponent"
        ));
        Assert.assertEquals(1, missing.size());
        Assert.assertEquals("non.existent.TruffleComponent", missing.get(0));
    }
}
