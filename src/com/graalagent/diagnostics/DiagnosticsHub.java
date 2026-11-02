package com.graalagent.diagnostics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiagnosticsHub {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String PREFIX = "[Graal-JS-Agent]";
    private static final String ANSI_RESET = "\u001B[0m";

    private static final DiagnosticsHub INSTANCE = new DiagnosticsHub();

    public static DiagnosticsHub getInstance() {
        return INSTANCE;
    }

    private volatile LogLevel minLevel = LogLevel.INFO;
    private volatile PrintWriter fileWriter;
    private final BlockingQueue<LogTask> logQueue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Dynamic Log4j 2 integration
    private volatile Object log4jLogger = null;
    private volatile Method log4jDebugMethod = null;
    private volatile Method log4jInfoMethod = null;
    private volatile Method log4jWarnMethod = null;
    private volatile Method log4jErrorMethod = null;
    private volatile boolean log4jProbed = false;

    public DiagnosticsHub() {
        this.workerThread = new Thread(this::processQueue, "GraalAgent-Diagnostics-Worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public synchronized void initFile(File logFile) {
        if (logFile == null) return;
        try {
            if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (this.fileWriter != null) {
                this.fileWriter.flush();
                this.fileWriter.close();
            }
            this.fileWriter = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8),
                    true
            );
        } catch (Throwable t) {
            System.err.println(PREFIX + " Failed to initialize log file " + logFile + ": " + t.getMessage());
        }
    }

    public LogLevel getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(LogLevel level) {
        if (level != null) {
            this.minLevel = level;
        }
    }

    public boolean isLoggable(LogLevel level) {
        return level != null && level.isAtLeast(this.minLevel);
    }

    public String formatConsole(LogLevel level, String message, LocalTime time) {
        String t = (time != null ? time : LocalTime.now()).format(TIME_FMT);
        String color = level != null ? level.getAnsiColor() : "";
        String lvlName = level != null ? level.name() : "INFO";
        return String.format("[%s] %s %s%s: %s%s", t, PREFIX, color, lvlName, message, ANSI_RESET);
    }

    public String formatFile(LogLevel level, String message, LocalTime time) {
        String t = (time != null ? time : LocalTime.now()).format(TIME_FMT);
        String lvlName = level != null ? level.name() : "INFO";
        return String.format("[%s] %s %s: %s", t, PREFIX, lvlName, message);
    }

    public void log(LogLevel level, String message, Throwable t) {
        if (!isLoggable(level)) {
            return;
        }
        LocalTime now = LocalTime.now();
        // Submit async to disk/worker
        logQueue.offer(new WriteLogEntry(level, message, t, now));

        // Console & Log4j dispatching
        dispatchConsoleAndLog4j(level, message, t, now);
    }

    private void dispatchConsoleAndLog4j(LogLevel level, String message, Throwable t, LocalTime time) {
        tryProbeLog4j();
        if (log4jLogger != null) {
            try {
                switch (level) {
                    case DEBUG -> {
                        if (log4jDebugMethod != null) log4jDebugMethod.invoke(log4jLogger, message);
                    }
                    case INFO, AUDIT -> {
                        if (log4jInfoMethod != null) log4jInfoMethod.invoke(log4jLogger, message);
                    }
                    case WARN -> {
                        if (log4jWarnMethod != null) log4jWarnMethod.invoke(log4jLogger, message);
                    }
                    case ERROR -> {
                        if (log4jErrorMethod != null) log4jErrorMethod.invoke(log4jLogger, message, t);
                    }
                }
                return;
            } catch (Throwable ignored) {
                // fallback to console
            }
        }

        String consoleOut = formatConsole(level, message, time);
        if (level == LogLevel.ERROR) {
            System.err.println(consoleOut);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        } else {
            System.out.println(consoleOut);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }

    private void tryProbeLog4j() {
        if (log4jProbed) return;
        synchronized (this) {
            if (log4jProbed) return;
            log4jProbed = true;
            try {
                Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
                Method getLogger = logManagerClass.getMethod("getLogger", String.class);
                this.log4jLogger = getLogger.invoke(null, "Graal-JS-Agent");
                Class<?> loggerClass = this.log4jLogger.getClass();
                this.log4jDebugMethod = loggerClass.getMethod("debug", String.class);
                this.log4jInfoMethod = loggerClass.getMethod("info", String.class);
                this.log4jWarnMethod = loggerClass.getMethod("warn", String.class);
                this.log4jErrorMethod = loggerClass.getMethod("error", String.class, Throwable.class);
            } catch (Throwable ignored) {
                // Log4j not present on classpath yet
            }
        }
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable t) {
        log(LogLevel.ERROR, message, t);
    }

    public void audit(String message) {
        log(LogLevel.AUDIT, message, null);
    }

    public void flush() {
        if (!running.get()) return;
        CountDownLatch latch = new CountDownLatch(1);
        logQueue.offer(new FlushTask(latch));
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            flush();
            workerThread.interrupt();
            synchronized (this) {
                if (fileWriter != null) {
                    fileWriter.flush();
                    fileWriter.close();
                    fileWriter = null;
                }
            }
        }
    }

    private void processQueue() {
        while (running.get() || !logQueue.isEmpty()) {
            try {
                LogTask task = logQueue.poll(200, TimeUnit.MILLISECONDS);
                if (task != null) {
                    task.execute(this);
                }
            } catch (InterruptedException e) {
                if (!running.get()) break;
            } catch (Throwable t) {
                System.err.println(PREFIX + " Error in logging worker: " + t.getMessage());
            }
        }
    }

    private interface LogTask {
        void execute(DiagnosticsHub hub);
    }

    private static class WriteLogEntry implements LogTask {
        private final LogLevel level;
        private final String message;
        private final Throwable throwable;
        private final LocalTime time;

        WriteLogEntry(LogLevel level, String message, Throwable throwable, LocalTime time) {
            this.level = level;
            this.message = message;
            this.throwable = throwable;
            this.time = time;
        }

        @Override
        public void execute(DiagnosticsHub hub) {
            if (hub.fileWriter != null) {
                String line = hub.formatFile(level, message, time);
                hub.fileWriter.println(line);
                if (throwable != null) {
                    throwable.printStackTrace(hub.fileWriter);
                }
            }
        }
    }

    private static class FlushTask implements LogTask {
        private final CountDownLatch latch;

        FlushTask(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void execute(DiagnosticsHub hub) {
            if (hub.fileWriter != null) {
                hub.fileWriter.flush();
            }
            latch.countDown();
        }
    }
}
