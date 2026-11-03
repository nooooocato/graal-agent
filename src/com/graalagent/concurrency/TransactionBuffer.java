package com.graalagent.concurrency;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Multi-Producer Single-Consumer (MPSC) transaction buffer for deferring state mutations
 * to the Minecraft main server tick loop.
 */
public final class TransactionBuffer {
    private static final Logger LOGGER = Logger.getLogger("Graal-JS-Agent");
    private static final Queue<TransactionAction> QUEUE = new ConcurrentLinkedQueue<>();
    public static final int MAX_BATCH_PER_TICK = 1000;

    private TransactionBuffer() {}

    /**
     * Enqueues a transaction action from any worker thread without locking.
     *
     * @param action the transaction action to enqueue
     */
    public static void enqueue(TransactionAction action) {
        if (action != null) {
            QUEUE.offer(action);
        }
    }

    /**
     * Flushes and applies pending actions up to MAX_BATCH_PER_TICK on the main thread.
     *
     * @param serverContext the server or game context
     * @return the number of actions successfully or exceptionally processed
     */
    public static int flush(Object serverContext) {
        int processed = 0;
        TransactionAction action;
        while (processed < MAX_BATCH_PER_TICK && (action = QUEUE.poll()) != null) {
            try {
                action.apply(serverContext);
            } catch (Throwable t) {
                // Catch any exception so that subsequent actions and the game loop are not interrupted
                LOGGER.log(Level.WARNING, "[TransactionBuffer] Error applying transaction action: " + t.getMessage(), t);
            }
            processed++;
        }
        return processed;
    }

    /**
     * Returns the current number of pending transactions.
     *
     * @return the number of queued actions
     */
    public static int size() {
        return QUEUE.size();
    }

    /**
     * Clears all pending transactions.
     */
    public static void clear() {
        QUEUE.clear();
    }
}
