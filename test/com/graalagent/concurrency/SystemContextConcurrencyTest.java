package com.graalagent.concurrency;

import com.graalagent.script.ScriptManager;
import com.graalagent.test.Assert;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SystemContextConcurrencyTest {

    public void testConcurrentPolyglotAccess() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-context-collision-test-");
        try {
            ScriptManager sm = new ScriptManager(tempDir.toFile());
            sm.start();

            int threadCount = 4;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Thread t = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 50; j++) {
                            Object res = sm.evalInline("10 + " + j);
                            if (res != null && String.valueOf(res).contains("Multi threaded access requested")) {
                                errorCount.incrementAndGet();
                            }
                        }
                    } catch (Throwable t1) {
                        if (t1.getMessage() != null && t1.getMessage().contains("Multi threaded access requested")) {
                            errorCount.incrementAndGet();
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                }, "test-concurrent-thread-" + threadId);
                t.setDaemon(true);
                t.start();
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            sm.stop();

            Assert.assertTrue(finished, "Threads should finish execution");
            // Green phase: with contextLock, errorCount is 0
            Assert.assertEquals(0, errorCount.get());
        } finally {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }

    public void testConcurrentClassLoaderAndTickAccess() throws Exception {
        Path tempDir = Files.createTempDirectory("graalagent-transformer-collision-test-");
        try {
            ScriptManager sm = new ScriptManager(tempDir.toFile());
            sm.start();

            // Register custom transformer callback in JS
            sm.evalInline("globalThis.transformCalls = 0; agent.registerTransformer(function(className, bytes) { globalThis.transformCalls++; return null; });");

            int threadCount = 4;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Thread t = new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 50; j++) {
                            if (threadId % 2 == 0) {
                                sm.onTick();
                            } else {
                                sm.applyCustomTransformers("net/minecraft/Class" + j, new byte[16]);
                            }
                        }
                    } catch (Throwable t1) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                }, "test-loader-thread-" + threadId);
                t.setDaemon(true);
                t.start();
            }

            startLatch.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            sm.stop();

            Assert.assertTrue(finished, "ClassLoader and tick threads should finish execution");
            Assert.assertEquals(0, errorCount.get());
        } finally {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }
}
