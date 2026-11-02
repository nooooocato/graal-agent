package com.mcagent.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String PREFIX = "[MC-JS-Agent]";
    private static volatile PrintWriter fileWriter = null;
    private static volatile Object log4jLogger = null;
    private static volatile Method log4jInfoMethod = null;
    private static volatile Method log4jWarnMethod = null;
    private static volatile Method log4jErrorMethod = null;

    static {
        initLogFile(new File("agent.log"));
    }

    public static synchronized void initLogFile(File logFile) {
        if (logFile == null) {
            return;
        }
        try {
            if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
            }
            fileWriter = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8),
                    true
            );
        } catch (Throwable t) {
            System.err.println("[MC-JS-Agent] Failed to initialize file logger at " + logFile.getPath() + ": " + t.getMessage());
        }
    }

    private static String time() {
        return LocalTime.now().format(TIME_FMT);
    }

    private static void tryInitLog4j() {
        if (log4jLogger != null) return;
        try {
            Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
            Method getLogger = logManagerClass.getMethod("getLogger", String.class);
            log4jLogger = getLogger.invoke(null, "MC-JS-Agent");
            Class<?> loggerClass = log4jLogger.getClass();
            log4jInfoMethod = loggerClass.getMethod("info", String.class);
            log4jWarnMethod = loggerClass.getMethod("warn", String.class);
            log4jErrorMethod = loggerClass.getMethod("error", String.class, Throwable.class);
        } catch (Throwable ignored) {
            // Log4j not yet loaded on classpath
        }
    }

    public static void info(String message) {
        String formatted = String.format("[%s] %s INFO: %s", time(), PREFIX, message);
        System.out.println(formatted);
        if (fileWriter != null) {
            fileWriter.println(formatted);
        }
        tryInitLog4j();
        if (log4jInfoMethod != null && log4jLogger != null) {
            try {
                log4jInfoMethod.invoke(log4jLogger, message);
            } catch (Throwable ignored) {}
        }
    }

    public static void warn(String message) {
        String formatted = String.format("[%s] %s WARN: %s", time(), PREFIX, message);
        System.out.println(formatted);
        if (fileWriter != null) {
            fileWriter.println(formatted);
        }
        tryInitLog4j();
        if (log4jWarnMethod != null && log4jLogger != null) {
            try {
                log4jWarnMethod.invoke(log4jLogger, message);
            } catch (Throwable ignored) {}
        }
    }

    public static void error(String message) {
        String formatted = String.format("[%s] %s ERROR: %s", time(), PREFIX, message);
        System.err.println(formatted);
        if (fileWriter != null) {
            fileWriter.println(formatted);
        }
        tryInitLog4j();
        if (log4jErrorMethod != null && log4jLogger != null) {
            try {
                log4jErrorMethod.invoke(log4jLogger, message, null);
            } catch (Throwable ignored) {}
        }
    }

    public static void error(String message, Throwable throwable) {
        String formatted = String.format("[%s] %s ERROR: %s", time(), PREFIX, message);
        System.err.println(formatted);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
        if (fileWriter != null) {
            fileWriter.println(formatted);
            if (throwable != null) {
                throwable.printStackTrace(fileWriter);
            }
        }
        tryInitLog4j();
        if (log4jErrorMethod != null && log4jLogger != null) {
            try {
                log4jErrorMethod.invoke(log4jLogger, message, throwable);
            } catch (Throwable ignored) {}
        }
    }
}
