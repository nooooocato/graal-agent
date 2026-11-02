package com.graalagent.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class RunJsRunner {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: RunJsRunner <path-to-js-file>");
            System.exit(1);
        }

        File jsFile = new File(args[0]);
        if (!jsFile.exists()) {
            System.err.println("File not found: " + jsFile.getAbsolutePath());
            System.exit(1);
        }

        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowAllAccess(true)
                .build()) {
            Source source = Source.newBuilder("js", jsFile)
                    .encoding(StandardCharsets.UTF_8)
                    .build();
            context.eval(source);
        } catch (Throwable t) {
            System.err.println("Error executing JavaScript:");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
