package com.graalagent.concurrency;

/**
 * Functional interface representing a state-mutating action deferred to the Minecraft main tick loop.
 */
@FunctionalInterface
public interface TransactionAction {
    /**
     * Applies the action on the game main thread.
     *
     * @param serverContext the server or game context object
     * @throws Exception if an error occurs during execution
     */
    void apply(Object serverContext) throws Exception;
}
