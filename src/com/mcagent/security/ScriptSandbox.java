package com.mcagent.security;

public class ScriptSandbox {
    private static final ThreadLocal<Boolean> sandboxed = ThreadLocal.withInitial(() -> false);

    public static boolean isSandboxed() {
        return Boolean.TRUE.equals(sandboxed.get());
    }

    public static void setSandboxed(boolean isSandboxed) {
        sandboxed.set(isSandboxed);
    }

    public static void clear() {
        sandboxed.remove();
    }
}
