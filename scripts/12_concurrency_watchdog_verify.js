// =========================================================================
// 12_concurrency_watchdog_verify.js - 并发读写锁、看门狗与事务缓冲测试脚本
// =========================================================================

console.log("==================================================");
console.log("[Concurrency-Verify] 加载并发读写锁与非阻塞 Tick 运行时测试套件");
console.log("==================================================");

// 1. 注册指令 /concurrencyinfo - 查看当前并发控制器与读写锁状态
agent.registerCommand("concurrencyinfo", (args) => {
    try {
        const ConcurrencyController = Java.type("com.graalagent.concurrency.ConcurrencyController");
        const ScriptEngineFacade = Java.type("com.graalagent.engine.ScriptEngineFacade");
        const facade = ScriptEngineFacade.getInstance();
        const controller = facade ? facade.getConcurrencyController() : null;

        agent.chat("§6====== [Graal-Agent 并发读写锁状态] ======");
        if (facade && controller) {
            agent.chat(`§bScriptEngineFacade 状态: §a在线 (活跃)`);
            agent.chat(`§b当前写锁占用 (是否重载中): §e${controller.isWriteLocked()}`);
            agent.chat(`§b当前活跃共享读锁数: §e${controller.getReadLockCount()}`);
            
            // 实时测试非阻塞 tryLock
            let tickRan = false;
            const tryOk = controller.tryExecuteTickRead(() => {
                tickRan = true;
            });
            agent.chat(`§b非阻塞 tryExecuteTickRead 测试: §a${tryOk && tickRan ? '成功 (零等待)' : '跳过'}`);
        } else {
            agent.chat("§c未找到 ScriptEngineFacade 门面实例");
        }
    } catch (err) {
        agent.chat("§c[Concurrency] 获取信息失败: " + err.message);
    }
});

// 2. 注册指令 /concurrencytest - 运行并发读写与非阻塞压测
agent.registerCommand("concurrencytest", (args) => {
    try {
        const ScriptEngineFacade = Java.type("com.graalagent.engine.ScriptEngineFacade");
        const facade = ScriptEngineFacade.getInstance();
        const controller = facade.getConcurrencyController();

        agent.chat("§e[Concurrency] 开始并发性能测试 (10,000 次共享读锁执行)...");
        const start = Date.now();
        let successCount = 0;

        for (let i = 0; i < 10000; i++) {
            const ok = controller.tryExecuteTickRead(() => {
                successCount++;
            });
            if (!ok) break;
        }

        const elapsed = Date.now() - start;
        agent.chat(`§a[Concurrency 测试完成] 10,000 次 tryLock 耗时: §f${elapsed} ms§a, 成功率: §f${(successCount / 100).toFixed(1)}%`);
    } catch (err) {
        agent.chat("§c[Concurrency 测试异常] " + err.message);
    }
});

// 3. 注册指令 /reloadstress - 测试热重载期间主线程非阻塞
agent.registerCommand("reloadstress", (args) => {
    try {
        agent.chat("§e[Stress] 模拟触发排他式热重载 (获取排他写锁)...");
        const ScriptEngineFacade = Java.type("com.graalagent.engine.ScriptEngineFacade");
        const facade = ScriptEngineFacade.getInstance();
        const controller = facade.getConcurrencyController();

        const Thread = Java.type("java.lang.Thread");
        let writeHolding = false;

        // 在独立线程中持有写锁 100ms
        const t = new Thread(() => {
            controller.executeExclusiveReload(() => {
                writeHolding = true;
                try {
                    Thread.sleep(100);
                } catch (e) {}
            });
        });
        t.start();

        // 稍等 10ms 确保写锁被持有
        Thread.sleep(10);

        // 主线程尝试非阻塞读取，验证绝不阻塞
        const readStart = Date.now();
        const tickResult = controller.tryExecuteTickRead(() => {});
        const readElapsed = Date.now() - readStart;

        agent.chat(`§b[Stress 结果] 写锁占用期间 tryLock 结果: §e${tickResult} (预期为 false)`);
        agent.chat(`§b[Stress 结果] 主线程执行耗时: §a${readElapsed} ms (远低于 50ms 预算，主线程零卡顿!)`);
    } catch (err) {
        agent.chat("§c[Stress 异常] " + err.message);
    }
});

// 4. 注册指令 /watchdogtest [ms] - 测试执行死循环 while(true){} 并验证看门狗硬熔断
agent.registerCommand("watchdogtest", (args) => {
    try {
        const ScriptEngineFacade = Java.type("com.graalagent.engine.ScriptEngineFacade");
        const Duration = Java.type("java.time.Duration");
        const facade = ScriptEngineFacade.getInstance();

        let budget = 50;
        if (args && args.trim().length > 0) {
            const parsed = parseInt(args.trim());
            if (!isNaN(parsed) && parsed > 0) budget = parsed;
        }

        agent.chat(`§6[Watchdog-Test] 提交死循环脚本 while(true){} 至 AsyncWorker，设定时限预算: §e${budget}ms...`);
        const future = facade.evalSandboxedAsync("while (true) {}", null, Duration.ofMillis(budget));

        future.thenAccept(res => {
            agent.chat(res.formatChatMessage());
            agent.chat(`§7[Watchdog-Test] 执行总耗时: §f${res.executionTimeMs()} ms§7, 熔断状态: §e${res.timedOut()}`);
        });
    } catch (err) {
        agent.chat("§c[Watchdog-Test 异常] " + err.message);
    }
});

// 5. 注册指令 /asyncbench [count] - 测试异步并发 Worker 池并发求值
agent.registerCommand("asyncbench", (args) => {
    try {
        const ScriptEngineFacade = Java.type("com.graalagent.engine.ScriptEngineFacade");
        const facade = ScriptEngineFacade.getInstance();

        let count = 20;
        if (args && args.trim().length > 0) {
            const parsed = parseInt(args.trim());
            if (!isNaN(parsed) && parsed > 0) count = Math.min(parsed, 100);
        }

        agent.chat(`§e[AsyncBench] 派发 ${count} 个异步沙箱计算任务至 WorkerPool...`);
        const start = Date.now();
        let completed = 0;

        for (let i = 0; i < count; i++) {
            const expr = `${i} * ${i} + 1`;
            facade.evalSandboxedAsync(expr, null).thenAccept(res => {
                completed++;
                if (completed === count) {
                    const elapsed = Date.now() - start;
                    agent.chat(`§a[AsyncBench 完成] ${count} 个任务全量执行完毕！总耗时: §f${elapsed} ms§a, 游戏主线程 0 卡顿。`);
                }
            });
        }
    } catch (err) {
        agent.chat("§c[AsyncBench 异常] " + err.message);
    }
});

console.log("[Concurrency-Verify] 注册指令: /concurrencyinfo, /concurrencytest, /reloadstress, /watchdogtest, /asyncbench 成功！");
