package com.graalagent.engine;

/**
 * 脚本求值执行结果模型
 */
public record ExecutionResult(
        Object returnValue,
        long executionTimeMs,
        boolean timedOut,
        Throwable error
) {
    public boolean isSuccess() {
        return error == null && !timedOut;
    }
}
