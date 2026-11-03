package com.graalagent.engine;

/**
 * 脚本求值执行结果不可变模型
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

    /**
     * 格式化为面向 Minecraft 玩家聊天栏的友好彩色提示字符串
     */
    public String formatChatMessage() {
        if (timedOut) {
            return "§c[安全熔断] 脚本执行超过 " + executionTimeMs + "ms 时间预算，已被看门狗强制终止！";
        }
        if (error != null) {
            String msg = error.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = error.getClass().getSimpleName();
            }
            return "§c[脚本错误] §f" + msg;
        }
        return "§e=> " + (returnValue != null ? returnValue : "null");
    }

    public static ExecutionResult success(Object value, long durationMs) {
        return new ExecutionResult(value, durationMs, false, null);
    }

    public static ExecutionResult timeout(long durationMs, Throwable cause) {
        return new ExecutionResult(null, durationMs, true, cause);
    }

    public static ExecutionResult failure(Throwable error, long durationMs) {
        return new ExecutionResult(null, durationMs, false, error);
    }
}
