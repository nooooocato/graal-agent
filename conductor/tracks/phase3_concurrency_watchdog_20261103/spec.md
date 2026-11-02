# Specification: 第三阶段：核心并发、看门狗防卡死与事务缓冲 (Concurrency, Watchdog & Transaction Buffer)

## Overview
本 Track 落实新一代架构技术蓝图第三阶段（参见 [docs/new_base/Refactoring-and-Migration-Plan.md](../../../docs/new_base/Refactoring-and-Migration-Plan.md)），旨在彻底解决多线程并发冲突，并终结**脚本编写 `while(true)` 死循环冻结 Minecraft 游戏主线程**的核心痛点。

详细设计规范参照：
- [Module-DualContext-Engine.md](../../../docs/new_base/Module-DualContext-Engine.md)
- [Module-Concurrency-and-Events.md](../../../docs/new_base/Module-Concurrency-and-Events.md)

---

## Functional Requirements

### 1. 读写锁解耦与锁竞争消除 (`ReadWriteLock`)
- 废弃 `ScriptManager` 对 `onTick()`、`onServerTick()`、`reload()` 的全类 `synchronized`；
- 引入读写锁分离：高频的主线程游戏心跳仅获取共享读锁（若恰逢极短暂重载则直接跳过而非阻塞挂起）；低频的热重载获取排他写锁。

### 2. 异步工作线程池 (`AsyncWorkerPool`)
- 所有来自游戏内玩家的 `/js` 指令求值，一律派发至独立的异步工作线程池（支持虚拟线程 Virtual Threads 或固定守护线程池），彻底剥离出 Minecraft 20 TPS 主线程循环。

### 3. 看门狗硬抢占中断系统 (`WatchdogTimer`)
- 任务派发时绑定一个看门狗看护令牌（WatchdogToken），设定 50ms（1 个 Tick）执行时间预算；
- **看门狗触发逻辑**：若任务在 50ms 内未完成（如发生 `while(true)` 死循环或死锁），看门狗线程立即调用 GraalVM Polyglot 底层的 `context.interrupt(Duration.ZERO)` 强制打断；
- 若中断后 50ms 仍未响应，强制触发 `context.close(true)` 销毁并重建容器；
- 向执行玩家聊天栏精准返回友好提示：`§c[安全熔断] 脚本执行超过 50ms 时间预算，已被看门狗强制终止！`，Minecraft 主游戏线程毫无卡顿，TPS 恒定保持 20.0。

### 4. 主线程事务缓冲队列 (`TransactionBuffer`)
- 采用无锁并发队列（MPSC）收集异步脚本对游戏状态的修改请求（`TransactionAction`）；
- 在主循环的 `onServerTick()` 阶段单线程批量消费并应用，彻底消除 `ConcurrentModificationException`。

### 5. 无锁并发事件总线 (`LockFreeEventBus`)
- 基于 `CopyOnWriteArrayList` 实现事件监听器管理；
- 支持事件优先级（`LOWEST`, `NORMAL`, `HIGH`, `MONITOR`）与取消标记传递（`Cancellable`）。

---

## Non-Functional Requirements
- **死循环熔断耗时**：从检测到死循环到成功抛出异常中断，总耗时 $\le 55\text{ ms}$；
- **主线程零等待**：主循环处理脚本事件耗时波动不超过 $0.2\text{ ms}$。

---

## Acceptance Criteria
- [ ] 针对异步 Worker 执行死循环 `while(true) {}` 的单元测试通过，验证看门狗在 50ms 左右精准抛出中断异常，且调用线程顺利返回；
- [ ] 针对并发多线程往 `TransactionBuffer` 投递 Action 的测试通过，验证在模拟主线程批量 flush 时数据 100% 正确应用；
- [ ] 验证主循环在频繁触发热重载时保持非阻塞。
