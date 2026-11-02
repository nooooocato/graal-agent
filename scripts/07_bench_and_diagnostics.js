// ==========================================
// 07_bench_and_diagnostics.js - 基础设施性能基准压测与遥测诊断脚本
// ==========================================

agent.registerCommand("benchmh", (args) => {
    agent.chat("§6[Benchmark] §e开始执行 MethodHandleCache vs 原生反射基准压测 (10,000 次调用)...");

    try {
        const MethodHandleCache = Java.type("com.graalagent.bridge.MethodHandleCache");
        const MethodType = Java.type("java.lang.invoke.MethodType");
        const StringClass = Java.type("java.lang.String").class;
        const ObjectClass = Java.type("java.lang.Object").class;

        // 1. 测试原生反射
        const startReflect = java.lang.System.nanoTime();
        const valueOfMethod = StringClass.getMethod("valueOf", ObjectClass);
        for (let i = 0; i < 10000; i++) {
            valueOfMethod.invoke(null, "benchmark_" + i);
        }
        const reflectNanos = java.lang.System.nanoTime() - startReflect;
        const reflectMs = (reflectNanos / 1000000).toFixed(2);

        // 2. 测试 MethodHandleCache
        const startMh = java.lang.System.nanoTime();
        const mhType = MethodType.methodType(StringClass, ObjectClass);
        const mh = MethodHandleCache.findStatic(StringClass, "valueOf", mhType);
        for (let i = 0; i < 10000; i++) {
            mh.invokeWithArguments("benchmark_" + i);
        }
        const mhNanos = java.lang.System.nanoTime() - startMh;
        const mhMs = (mhNanos / 1000000).toFixed(2);

        const ratio = ((reflectNanos - mhNanos) / reflectNanos * 100).toFixed(1);
        agent.chat(`§a[Benchmark 完成] §f原生反射耗时: §c${reflectMs} ms §7| §fMethodHandle 耗时: §a${mhMs} ms §7(性能提升: §e${ratio}%§7)`);
        agent.log(`[Benchmark] Reflect: ${reflectMs}ms, MethodHandle: ${mhMs}ms, Improvement: ${ratio}%`);
    } catch (e) {
        agent.chat(`§c[Benchmark 异常] ${e}`);
        agent.log(`Benchmark error: ${e}`);
    }
});

agent.registerCommand("crashdump", (args) => {
    try {
        const CrashDumpRecorder = Java.type("com.graalagent.diagnostics.CrashDumpRecorder");
        const HashMap = Java.type("java.util.HashMap");
        const recorder = new CrashDumpRecorder();

        const state = new HashMap();
        state.put("Active Modlets", "[magic_system (v1.0.0), virtual_pack (v0.5)]");
        state.put("Context State", "READY");
        state.put("Triggered By", "In-Game Command /crashdump");

        const simulatedError = new java.lang.RuntimeException("用户手动请求生成崩溃与环境诊断快照 (Manual in-game diagnostic snapshot)");
        const dumpFile = recorder.recordCrash(simulatedError, "In-Game Debug Trigger", state);

        agent.chat(`§a[Diagnostics] §f已成功生成系统崩溃与诊断快照: §e${dumpFile.getName()}`);
        agent.chat(`§7快照保存路径: §f${dumpFile.getAbsolutePath()}`);
        agent.log(`[Diagnostics] Crash dump recorded: ${dumpFile.getAbsolutePath()}`);
    } catch (e) {
        agent.chat(`§c[Diagnostics 异常] ${e}`);
    }
});

agent.registerCommand("diaglog", (args) => {
    try {
        const DiagnosticsHub = Java.type("com.graalagent.diagnostics.DiagnosticsHub");
        const hub = DiagnosticsHub.getInstance();
        hub.info("In-game test: INFO 日志测试 (绿色高亮)");
        hub.warn("In-game test: WARN 警告测试 (金黄色高亮)");
        hub.error("In-game test: ERROR 错误测试 (红色高亮)");
        hub.audit("In-game test: AUDIT 安全审计测试 (紫色高亮)");
        agent.chat("§a[Diagnostics] §f已向控制台与日志文件发送多级别 ANSI 结构化日志测试，请查看控制台与 agent.log。");
    } catch (e) {
        agent.chat(`§c[Diagnostics 异常] ${e}`);
    }
});

agent.log("Loaded 07_bench_and_diagnostics.js (Commands: /benchmh, /crashdump, /diaglog)");
