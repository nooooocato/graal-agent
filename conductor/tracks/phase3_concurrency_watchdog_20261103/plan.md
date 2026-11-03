# Implementation Plan: 第三阶段：核心并发、看门狗防卡死与事务缓冲 (Concurrency, Watchdog & Transaction Buffer)

## Phase 1: 读写锁与并发骨架 (ReadWriteLock Architecture) [checkpoint: ef45c27]
- [x] Task: 编写读写锁竞争与非阻塞 Tick 单元测试 (Red Phase) fb970cd
    - [x] 模拟重载期间主线程尝试 tick，验证 tryLock 成功避免挂起
- [x] Task: 重构 ScriptEngineFacade 并发锁策略 (Green Phase) 0f8d225
    - [x] 引入 `ReentrantReadWriteLock` 替代原先粗粒度 `synchronized`
    - [x] 实现非阻塞的 `onTick()` 与安全的独占式 `reload()`
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) ef45c27

## Phase 2: 异步 Worker 线程池与看门狗 (AsyncWorker & Watchdog) [checkpoint: ea4bbc4]
- [x] Task: 编写 WatchdogTimer 中断死循环单元测试 (Red Phase) b4eda17
    - [x] 编写测试案例：执行 `while(true){}`，期望 50ms 触发 `PolyglotException.isCancelled()`
- [x] Task: 实现 AsyncWorkerPool 与 WatchdogTimer (Green Phase) 6bf37c2
    - [x] 创建 `com.graalagent.concurrency.AsyncWorkerPool`
    - [x] 创建 `com.graalagent.concurrency.WatchdogTimer` 与 `WatchdogToken`
    - [x] 实现针对超时调用 `context.interrupt(Duration.ZERO)` 的强行熔断
- [x] Task: 熔断错误友好封装与重试机制 (Refactor Phase) 2359e90
    - [x] 编写优雅的错误消息与超时结果返回对象
- [x] Task: 系统容器单线程互斥与防并发踩踏 (ContextLock Protection) 090dd9f
    - [x] 编写多线程（如异步类加载器与主线程）并发踏入系统 Context 单元测试 (Red Phase) 65634a0
    - [x] 在 ConcurrencyController 与 ScriptManager 引入 contextLock 互斥，消除 Multi threaded access requested (Green Phase) 090dd9f
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) ea4bbc4

## Phase 3: 主线程事务缓冲队列 (TransactionBuffer)
- [x] Task: 编写 TransactionBuffer 并发入队与主线程批处理测试 (Red Phase) 6fd01e1
    - [x] 多线程并发并发投递 1000 个 Action 测试
    - [x] 模拟主线程单批次处理上限（MAX_BATCH）截断测试
- [ ] Task: 实现 TransactionBuffer 与核心 Action 接口 (Green Phase)
    - [ ] 创建 `com.graalagent.concurrency.TransactionBuffer`
    - [ ] 创建 `com.graalagent.concurrency.TransactionAction` 函数式接口
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 4: 无锁并发事件总线 (LockFreeEventBus)
- [ ] Task: 编写 LockFreeEventBus 优先级与取消机制测试 (Red Phase)
    - [ ] 验证高优先级监听器优先执行且可阻断后续派发
- [ ] Task: 实现 LockFreeEventBus (Green Phase)
    - [ ] 创建 `com.graalagent.script.event.LockFreeEventBus`
    - [ ] 支持基于 `CopyOnWriteArrayList` 的高并发无锁只读遍历
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
