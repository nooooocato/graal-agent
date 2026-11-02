package com.graalagent.diagnostics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class CrashDumpRecorder {
    private static final DateTimeFormatter DUMP_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final File dumpDir;

    public CrashDumpRecorder() {
        this(new File("dumps"));
    }

    public CrashDumpRecorder(File dumpDir) {
        this.dumpDir = dumpDir;
    }

    public File recordCrash(Throwable error, String contextInfo, Map<String, Object> extraState) {
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FMT);
        File dumpFile = new File(dumpDir, "agent-crash-" + timestamp + ".dump");

        String formattedDump = formatDump(error, contextInfo, extraState);
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(dumpFile), StandardCharsets.UTF_8))) {
            writer.print(formattedDump);
            writer.flush();
        } catch (Throwable t) {
            System.err.println("[Graal-JS-Agent] Failed to write crash dump file " + dumpFile.getPath() + ": " + t.getMessage());
        }

        return dumpFile;
    }

    public String formatDump(Throwable error, String contextInfo, Map<String, Object> extraState) {
        StringBuilder sb = new StringBuilder();
        String headerTime = LocalDateTime.now().format(DUMP_TIMESTAMP_FMT);

        sb.append("================================================================================\n");
        sb.append("GRAAL-AGENT CRASH DUMP REPORT - ").append(headerTime).append("\n");
        sb.append("================================================================================\n\n");

        sb.append("Runtime Environment:\n");
        sb.append("  Minecraft Version: 26.3\n");
        sb.append("  Java Version: ")
                .append(System.getProperty("java.version", "unknown"))
                .append(" (").append(System.getProperty("java.vendor", "unknown")).append(")\n");
        sb.append("  Java VM: ")
                .append(System.getProperty("java.vm.name", "unknown")).append("\n");
        sb.append("  Operating System: ")
                .append(System.getProperty("os.name", "unknown"))
                .append(" (").append(System.getProperty("os.arch", "unknown"))
                .append(", v").append(System.getProperty("os.version", "unknown")).append(")\n\n");

        sb.append("Agent & Engine State:\n");
        if (contextInfo != null && !contextInfo.isBlank()) {
            sb.append("  Context Info: ").append(contextInfo).append("\n");
        }
        if (extraState != null && !extraState.isEmpty()) {
            for (Map.Entry<String, Object> entry : extraState.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("\n");

        sb.append("Exception Information:\n");
        if (error != null) {
            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                error.printStackTrace(pw);
            }
            sb.append(sw.toString());
        } else {
            sb.append("  No exception provided.\n");
        }

        sb.append("\n================================================================================\n");
        return sb.toString();
    }
}
