package com.graalagent.concurrency;

import com.graalagent.test.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyControllerTest {

    public void testNormalTickExecutes() {
        ConcurrencyController controller = new ConcurrencyController();
        AtomicBoolean executed = new AtomicBoolean(false);

        boolean result = controller.tryExecuteTickRead(() -> executed.set(true));

        Assert.assertTrue(result, "tryExecuteTickRead should return true when no reload is in progress");
        Assert.assertTrue(executed.get(), "Tick action should have executed");
    }

    public void testNonBlockingTickSkipsDuringReload() throws Exception {
        ConcurrencyController controller = new ConcurrencyController();
        CountDownLatch reloadStarted = new CountDownLatch(1);
        CountDownLatch canFinishReload = new CountDownLatch(1);
        AtomicBoolean reloadExecuted = new AtomicBoolean(false);
        AtomicBoolean tickExecuted = new AtomicBoolean(false);

        Thread reloadThread = new Thread(() -> {
            controller.executeExclusiveReload(() -> {
                reloadStarted.countDown();
                reloadExecuted.set(true);
                try {
                    canFinishReload.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
            });
        }, "test-reload-thread");
        reloadThread.setDaemon(true);
        reloadThread.start();

        // Wait until reload acquires write lock
        Assert.assertTrue(reloadStarted.await(1, TimeUnit.SECONDS), "Reload thread should have started");

        long start = System.currentTimeMillis();
        boolean tickResult = controller.tryExecuteTickRead(() -> tickExecuted.set(true));
        long duration = System.currentTimeMillis() - start;

        // In a non-blocking tryLock design:
        // 1. tickResult must be false because reload is holding write lock
        // 2. tick action must not be executed
        // 3. duration must be immediate (< 50ms, typically 0ms), never blocking the main thread!
        Assert.assertFalse(tickResult, "Tick should have been skipped during active reload");
        Assert.assertFalse(tickExecuted.get(), "Tick action must not execute during active reload");
        Assert.assertTrue(duration < 50, "tryExecuteTickRead took " + duration + "ms, but must not block the main thread!");

        // Release reload
        canFinishReload.countDown();
        reloadThread.join(2000);

        Assert.assertTrue(reloadExecuted.get(), "Reload action should have completed");

        // Now subsequent tick should succeed
        AtomicBoolean tickAfterReload = new AtomicBoolean(false);
        boolean resultAfter = controller.tryExecuteTickRead(() -> tickAfterReload.set(true));
        Assert.assertTrue(resultAfter, "Tick after reload finishes should succeed");
        Assert.assertTrue(tickAfterReload.get(), "Tick after reload finishes should execute");
    }

    public void testConcurrentTickReadsAllowed() throws Exception {
        ConcurrencyController controller = new ConcurrencyController();
        int threadCount = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await();
                    boolean ok = controller.tryExecuteTickRead(() -> {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException ignored) {}
                    });
                    if (ok) successCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        startLatch.countDown();
        Assert.assertTrue(doneLatch.await(2, TimeUnit.SECONDS), "All concurrent tick threads should finish");
        Assert.assertEquals(threadCount, successCount.get());
    }

    public void testScriptManagerNonBlockingTick() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("graalagent-concurrency-test-");
        try {
            com.graalagent.script.ScriptManager sm = new com.graalagent.script.ScriptManager(tempDir.toFile());
            sm.start();

            // Normal tick executes
            boolean tick1 = sm.onTick();
            Assert.assertTrue(tick1, "Initial onTick should succeed");

            CountDownLatch reloadStarted = new CountDownLatch(1);
            CountDownLatch canFinishReload = new CountDownLatch(1);

            Thread reloadThread = new Thread(() -> {
                sm.getConcurrencyController().executeExclusiveReload(() -> {
                    reloadStarted.countDown();
                    try {
                        canFinishReload.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}
                });
            });
            reloadThread.setDaemon(true);
            reloadThread.start();

            Assert.assertTrue(reloadStarted.await(1, TimeUnit.SECONDS), "Reload should start");

            long start = System.currentTimeMillis();
            boolean tickSkipped = sm.onTick();
            long duration = System.currentTimeMillis() - start;

            Assert.assertFalse(tickSkipped, "onTick must return false and skip during active reload");
            Assert.assertTrue(duration < 50, "onTick took " + duration + "ms, but must never block main thread");

            canFinishReload.countDown();
            reloadThread.join(2000);

            boolean tickAfter = sm.onTick();
            Assert.assertTrue(tickAfter, "onTick after reload should succeed");
            sm.stop();
        } finally {
            try (var stream = java.nio.file.Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }

    public void testScriptEngineFacadeIntegration() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("graalagent-facade-test-");
        try {
            com.graalagent.script.ScriptManager sm = new com.graalagent.script.ScriptManager(tempDir.toFile());
            sm.start();

            com.graalagent.engine.ScriptEngineFacade facade = com.graalagent.engine.ScriptEngineFacade.getInstance();
            Assert.assertNotNull(facade, "ScriptEngineFacade should be initialized");
            Assert.assertNotNull(facade.getConcurrencyController(), "ConcurrencyController should not be null");

            com.graalagent.engine.ExecutionResult result = facade.evalPrivileged("21 * 2");
            Assert.assertTrue(result.isSuccess(), "evalPrivileged should succeed");
            Assert.assertEquals("42", String.valueOf(result.returnValue()));
            sm.stop();
        } finally {
            try (var stream = java.nio.file.Files.walk(tempDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .map(java.nio.file.Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
    }
}
