package com.graalagent.concurrency;

import com.graalagent.test.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionBufferTest {

    public void testConcurrentEnqueueAndBatchFlush() throws Exception {
        TransactionBuffer.clear();
        int threadCount = 10;
        int perThreadActions = 100;
        int totalExpected = threadCount * perThreadActions; // 1000 actions

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger processedCounter = new AtomicInteger(0);
        List<Integer> appliedItems = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < perThreadActions; i++) {
                        final int value = threadId * 1000 + i;
                        TransactionBuffer.enqueue(ctx -> {
                            appliedItems.add(value);
                            processedCounter.incrementAndGet();
                        });
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        Assert.assertTrue(completed, "All producer threads should finish enqueuing within 5 seconds");

        Assert.assertEquals(totalExpected, TransactionBuffer.size());

        // Simulate Minecraft main thread tick flush
        Object mockServerContext = "MockServerContext";
        int flushed = TransactionBuffer.flush(mockServerContext);

        Assert.assertEquals(totalExpected, flushed);
        Assert.assertEquals(0, TransactionBuffer.size());
        Assert.assertEquals(totalExpected, processedCounter.get());
        Assert.assertEquals(totalExpected, appliedItems.size());
    }

    public void testBatchLimitTruncation() {
        TransactionBuffer.clear();
        int totalToEnqueue = 1500;
        AtomicInteger executedCount = new AtomicInteger(0);

        for (int i = 0; i < totalToEnqueue; i++) {
            TransactionBuffer.enqueue(ctx -> executedCount.incrementAndGet());
        }

        Assert.assertEquals(totalToEnqueue, TransactionBuffer.size());

        // First tick flush: should process up to MAX_BATCH_PER_TICK (1000)
        int firstFlush = TransactionBuffer.flush(null);
        Assert.assertEquals(TransactionBuffer.MAX_BATCH_PER_TICK, firstFlush);
        Assert.assertEquals(TransactionBuffer.MAX_BATCH_PER_TICK, executedCount.get());
        Assert.assertEquals(500, TransactionBuffer.size());

        // Second tick flush: should process remaining 500
        int secondFlush = TransactionBuffer.flush(null);
        Assert.assertEquals(500, secondFlush);
        Assert.assertEquals(1500, executedCount.get());
        Assert.assertEquals(0, TransactionBuffer.size());
    }

    public void testActionExceptionIsolation() {
        TransactionBuffer.clear();
        AtomicInteger successCounter = new AtomicInteger(0);

        TransactionBuffer.enqueue(ctx -> successCounter.incrementAndGet());
        TransactionBuffer.enqueue(ctx -> {
            throw new RuntimeException("Simulated action failure");
        });
        TransactionBuffer.enqueue(ctx -> successCounter.incrementAndGet());

        Assert.assertEquals(3, TransactionBuffer.size());

        int processed = TransactionBuffer.flush(null);
        // Even if action 2 throws, action 1 and 3 succeed and queue is drained
        Assert.assertEquals(2, successCounter.get());
        Assert.assertEquals(0, TransactionBuffer.size());
    }

    public void testClearAndNullSafety() {
        TransactionBuffer.clear();
        TransactionBuffer.enqueue(null); // Should be safely ignored
        Assert.assertEquals(0, TransactionBuffer.size());

        TransactionBuffer.enqueue(ctx -> {});
        TransactionBuffer.enqueue(ctx -> {});
        Assert.assertEquals(2, TransactionBuffer.size());

        TransactionBuffer.clear();
        Assert.assertEquals(0, TransactionBuffer.size());
    }
}
