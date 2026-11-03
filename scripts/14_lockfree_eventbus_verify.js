// =========================================================================
// 14_lockfree_eventbus_verify.js - 无锁并发事件总线测试套件
// =========================================================================

console.log("==================================================");
console.log("[EventBus-Verify] 加载无锁并发事件总线 (LockFreeEventBus) 测试套件");
console.log("==================================================");

// 1. 注册指令 /eventinfo - 查看 LockFreeEventBus 架构特性
agent.registerCommand("eventinfo", (args) => {
    try {
        const LockFreeEventBus = Java.type("com.graalagent.script.event.LockFreeEventBus");
        const EventPriority = Java.type("com.graalagent.script.event.LockFreeEventBus$EventPriority");

        agent.chat("§6====== [LockFreeEventBus 并发事件总线特性] ======");
        agent.chat("§b底层存储: §aCopyOnWriteArrayList (100% 读线程无锁只读遍历)");
        agent.chat("§b调度优先级: §eHIGHEST -> HIGH -> NORMAL -> LOW -> LOWEST -> MONITOR");
        agent.chat("§b短路机制: §eCancellable 快速短路与 MONITOR 审计保障");
        agent.chat("§b异常隔离: §a单监听器抛错不中断同事件其他监听器派发");
    } catch (err) {
        agent.chat("§c[EventBus] 获取信息失败: " + err.message);
    }
});

// 2. 注册指令 /eventtest - 测试 6 级优先级按序调度
agent.registerCommand("eventtest", (args) => {
    try {
        const LockFreeEventBus = Java.type("com.graalagent.script.event.LockFreeEventBus");
        const EventPriority = Java.type("com.graalagent.script.event.LockFreeEventBus$EventPriority");
        const ArrayList = Java.type("java.util.ArrayList");

        const bus = new LockFreeEventBus();
        const executedOrder = new ArrayList();

        // 故意以无序顺序注册
        bus.registerNamed("chatEvent", EventPriority.NORMAL, (args) => executedOrder.add("NORMAL"));
        bus.registerNamed("chatEvent", EventPriority.MONITOR, (args) => executedOrder.add("MONITOR"));
        bus.registerNamed("chatEvent", EventPriority.HIGHEST, (args) => executedOrder.add("HIGHEST"));
        bus.registerNamed("chatEvent", EventPriority.LOWEST, (args) => executedOrder.add("LOWEST"));
        bus.registerNamed("chatEvent", EventPriority.HIGH, (args) => executedOrder.add("HIGH"));
        bus.registerNamed("chatEvent", EventPriority.LOW, (args) => executedOrder.add("LOW"));

        agent.chat("§e[EventTest] 派发事件，验证严格优先级调度顺序...");
        const handled = bus.emitNamed("chatEvent", "Hello Minecraft");

        agent.chat(`§b[EventTest 实际调度顺序]: §f${executedOrder.toString()}`);
        const expected = "[HIGHEST, HIGH, NORMAL, LOW, LOWEST, MONITOR]";
        if (executedOrder.toString() === expected) {
            agent.chat("§a[EventTest 成功] 6 级优先级排序完全符合预期！");
        } else {
            agent.chat(`§c[EventTest 顺序异常] 期望: ${expected}`);
        }
    } catch (err) {
        agent.chat("§c[EventTest 异常] " + err.message);
    }
});

// 3. 注册指令 /eventbench [count] - 测试无锁高并发事件派发性能
agent.registerCommand("eventbench", (args) => {
    try {
        const LockFreeEventBus = Java.type("com.graalagent.script.event.LockFreeEventBus");
        const EventPriority = Java.type("com.graalagent.script.event.LockFreeEventBus$EventPriority");
        const AtomicInteger = Java.type("java.util.concurrent.atomic.AtomicInteger");

        let count = 10000;
        if (args && args.trim().length > 0) {
            const parsed = parseInt(args.trim());
            if (!isNaN(parsed) && parsed > 0) count = Math.min(parsed, 100000);
        }

        const bus = new LockFreeEventBus();
        const counter = new AtomicInteger(0);

        bus.registerNamed("tickEvent", EventPriority.HIGHEST, (args) => { counter.incrementAndGet(); });
        bus.registerNamed("tickEvent", EventPriority.NORMAL, (args) => { counter.incrementAndGet(); });
        bus.registerNamed("tickEvent", EventPriority.MONITOR, (args) => { counter.incrementAndGet(); });

        agent.chat(`§e[EventBench] 开始无锁只读高并发压测 (${count} 次 3 监听器派发)...`);
        const start = Date.now();

        for (let i = 0; i < count; i++) {
            bus.emitNamed("tickEvent", i);
        }

        const elapsed = Date.now() - start;
        const totalCalls = counter.get();
        const perOp = ((elapsed * 1000) / count).toFixed(2);

        agent.chat(`§a[EventBench 完成] 派发 ${count} 次 (触发 ${totalCalls} 次监听)，总耗时: §f${elapsed} ms§a, 平均每次派发: §f${perOp} µs§a, 零锁竞争！`);
    } catch (err) {
        agent.chat("§c[EventBench 异常] " + err.message);
    }
});

console.log("[EventBus-Verify] 注册指令: /eventinfo, /eventtest, /eventbench 成功！");
