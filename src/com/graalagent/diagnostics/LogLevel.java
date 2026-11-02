package com.graalagent.diagnostics;

public enum LogLevel {
    DEBUG(0, "\u001B[36m"),
    INFO(1, "\u001B[32m"),
    WARN(2, "\u001B[33m"),
    ERROR(3, "\u001B[31m"),
    AUDIT(4, "\u001B[35m");

    private final int priority;
    private final String ansiColor;

    LogLevel(int priority, String ansiColor) {
        this.priority = priority;
        this.ansiColor = ansiColor;
    }

    public int getPriority() {
        return priority;
    }

    public String getAnsiColor() {
        return ansiColor;
    }

    public boolean isAtLeast(LogLevel other) {
        return this.priority >= other.priority;
    }
}
