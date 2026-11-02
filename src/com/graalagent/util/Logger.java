package com.graalagent.util;

import com.graalagent.diagnostics.DiagnosticsHub;

import java.io.File;

/**
 * Backward-compatible delegating logger facade.
 * Delegates all operations to DiagnosticsHub.
 */
public class Logger {
    private static final DiagnosticsHub HUB = DiagnosticsHub.getInstance();

    static {
        HUB.initFile(new File("agent.log"));
    }

    public static synchronized void initLogFile(File logFile) {
        HUB.initFile(logFile);
    }

    public static void logToFileOnly(String message) {
        HUB.logToFileOnly(message);
    }

    public static void info(String message) {
        HUB.info(message);
    }

    public static void warn(String message) {
        HUB.warn(message);
    }

    public static void error(String message) {
        HUB.error(message);
    }

    public static void error(String message, Throwable throwable) {
        HUB.error(message, throwable);
    }

    public static void flush() {
        HUB.flush();
    }
}
