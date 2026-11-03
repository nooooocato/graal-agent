---
title: 模块规范：双容器隔离引擎与看门狗抢占机制 (DualContext Engine)
date: 2026-11-03
tags:
  - "#/module"
  - "#/dual-context"
  - "#/polyglot"
  - "#/watchdog"
  - "#/anti-hang"
  - "#/new-base"
---

# 模块规范：双容器隔离引擎与看门狗抢占机制 (DualContext Engine)

本模块规范是新架构解决“单容器安全污染”以及“脚本恶意或无意书写死循环冻结整个游戏”两大核心痛点的关键设计。

---

## 🏗️ 核心设计全景：双容器物理隔离与异步看门狗

在前代设计中，控制台特权与玩家指令共享同一个 Polyglot `Context`，仅凭一个简单的 `ThreadLocal` 区分权限，不仅存在严重的锁竞争，更无法抵御异步逃逸与主线程卡死。

新架构确立了 **双容器物理隔离 (Dual-Context Physical Separation)** 与 **异步 Worker + 看门狗强制抢占中断 (Async Worker + Watchdog Preemption)** 体系：

```mermaid
flowchart TD
    classDef engine fill:#0f172a,stroke:#38bdf8,color:#fff
    classDef sys fill:#1e3a8a,stroke:#3b82f6,color:#fff
    classDef sbx fill:#831843,stroke:#ec4899,color:#fff
    classDef wd fill:#b45309,stroke:#f59e0b,color:#fff
    classDef sync fill:#065f46,stroke:#10b981,color:#fff

    subgraph Engine["ScriptEngineFacade (引擎总控)"]
        Router["CommandRouter / Evaluator"]:::engine
    end

    subgraph DualContext["双容器物理隔离模型"]
        subgraph SysContext["1. 特权系统容器 (System Context)"]
            SysEngine["常驻单例 Context\n- 运行核心系统脚本与 ESM Modlets\n- 响应常驻 Tick / Server 生命周期\n- 允许受控 HostAccess 与只读文件系统\n- 控制台专用直接求值"]:::sys
        end

        subgraph SandboxContext["2. 零信任沙箱容器池 (Player Sandbox Context Pool)"]
            SbxEngine["隔离沙箱 Context 池\n- 仅供游戏内玩家执行指令求值\n- 彻底剥离 Java 反射与 Java.type\n- 全局原型链深度冻结 (Object.freeze)\n- 仅暴露纯只读/受控的领域 DTO 代理"]:::sbx
        end
    end

    subgraph AsyncWatchdog["异步看门狗防卡死子系统"]
        WorkerPool["AsyncWorkerPool (专用虚拟线程池 / 守护线程池)"]:::wd
        Watchdog["WatchdogTimer (独立高优先级守护定时器)"]:::wd
        Interrupt["context.interrupt(Duration.ZERO)\nTruffle 底层硬熔断中断机制"]:::wd
    end

    subgraph MainThreadSync["主线程安全回写"]
        TxBuffer["TransactionBuffer (原子事务缓冲队列)"]:::sync
        MC["Minecraft Server/Client 主线程"]:::sync
    end

    Router -->|控制台或系统服务| SysContext
    Router -->|游戏内玩家指令| WorkerPool
    WorkerPool --> SandboxContext
    WorkerPool -->|注册任务令牌 (50ms 预算)| Watchdog
    Watchdog -->|超时未完成!| Interrupt
    Interrupt -->|强行打断死循环!| SandboxContext
    SandboxContext -->|合法世界修改请求| TxBuffer
    TxBuffer -->|Tick 边界批量原子刷新| MC
```

---

## 彻底终结死循环卡死的机制剖析

在游戏脚本运行中，最致命的场景是玩家在聊天框输入无限循环脚本（如密集 `while(true)` 或递归计算）。若在游戏主线程直接执行，主游戏循环将瞬间被挂起，引发游戏崩溃。

新架构通过以下三阶机制彻底消除该隐患：

### 1. 第一阶：计算与主游戏循环物理脱耦 (Async Offloading)
所有来自游戏内玩家的指令求值，**一律禁止在 Minecraft 主线程直接执行**，必须派发至专用的 `AsyncWorkerPool`（基于 JDK 21+ 虚拟线程 Virtual Threads 或固定守护线程池）：
- 无论脚本计算多么复杂，或者发生何种阻塞，Minecraft 主游戏循环（渲染、网络发包、物理碰撞、生物 AI）继续按照 20 TPS 丝滑运行，毫无感知。

### 2. 第二阶：看门狗超时硬中断 (`context.interrupt`)
在任务提交至 Worker 的同时，向独立运行的高精度 `WatchdogTimer` 注册看护令牌，设定执行时间预算（默认 **50ms**，即一个 Minecraft 游戏刻的时间）：
- 当任务在 50ms 内正常结束，看护令牌被自动注销；
- 若任务超过 50ms 仍未结束，看门狗在独立高优先级线程中直接调用 `sandboxContext.interrupt(Duration.ZERO)`。

#### `context.interrupt(Duration.ZERO)` 的底层原理
GraalVM Truffle 引擎在编译生成的机器码中内插了安全检查点（Safepoints）。一旦外部调用强制中断：
- Truffle 运行时会在下一次循环迭代或跳转指令处**瞬间捕获中断请求**；
- 立即在循环内部抛出未受检的 `PolyglotException` 并展开调用栈，跳出循环；
- 整个中断过程耗时在微秒级，即使是没有任何函数调用的极简死循环，也能被百分之百精准中断并回收沙箱。

### 3. 第三阶：终极防线 (Forced Context Close)
若某些原生阻塞导致 `context.interrupt` 在随后的 50ms 内仍未释放，看门狗第二阶段直接调用 `sandboxContext.close(true)` 强行销毁该隔离容器，并从线程池驱逐对应线程，重新向池中注入新的纯净 Context，确保系统永远自愈。

---

## ⚙️ 核心数据模板与 API 接口契约

### 1. 脚本求值请求与结果数据模板 (`ExecutionModels`)

```java
package com.graalagent.engine;

import java.time.Duration;

/** 脚本求值请求不可变模型 */
public record ExecutionRequest(
        String script,
        Object commandSource,
        boolean privileged,
        Duration timeout
) {}

/** 脚本求值执行结果模型 */
public record ExecutionResult(
        Object returnValue,
        long executionTimeMs,
        boolean timedOut,
        Throwable error
) {
    public boolean isSuccess() {
        return error == null && !timedOut;
    }
}
```

---

### 2. 脚本引擎统一门面 API 契约 (`ScriptEngineFacade`)

```java
package com.graalagent.engine;

import java.util.concurrent.CompletableFuture;

public class ScriptEngineFacade {

    /** 获取全局门面单例 */
    public static ScriptEngineFacade getInstance();

    /**
     * 特权同步执行：供控制台或核心生命周期钩子调用
     */
    public ExecutionResult evalPrivileged(String script);

    /**
     * 玩家零信任沙箱异步求值：派发至 AsyncWorkerPool 并搭载 50ms 超时看门狗
     */
    public CompletableFuture<ExecutionResult> evalSandboxedAsync(String script, Object commandSource);

    /** 触发全量脚本与 Modlet 热重载 (获取排他写锁) */
    public void reloadScripts();

    /** 优雅关闭所有容器池并释放资源 */
    public void shutdown();
}
```

---

### 3. 超时看门狗抢占 API 契约 (`WatchdogTimer`)

```java
package com.graalagent.engine;

import org.graalvm.polyglot.Context;
import java.time.Duration;

public final class WatchdogTimer {
    /** 默认主刻时限预算: 50 毫秒 */
    public static final Duration DEFAULT_BUDGET = Duration.ofMillis(50);

    public interface WatchdogTask {
        void cancel();
        boolean hasTriggered();
    }

    /**
     * 向高精度看门狗注册监听任务
     *
     * @param context 要守护的 Polyglot 容器
     * @param budget  执行时限预算 (超期即触发 context.interrupt)
     */
    public static WatchdogTask schedule(Context context, Duration budget);
}
```

---

## 🔄 主线程状态修改的事务缓冲 (`TransactionBuffer`)

由于沙箱代码在异步 Worker 线程中运行，而 Minecraft 的世界与实体操作（如放置方块、给予物品）必须在主线程执行，架构规范严禁沙箱直接触碰原生 Minecraft 指针，采用**只读副本 + 事务缓冲（Transactional Command Buffer）**模型：

```mermaid
flowchart LR
    classDef js fill:#831843,stroke:#ec4899,color:#fff
    classDef proxy fill:#1e3a8a,stroke:#3b82f6,color:#fff
    classDef q fill:#065f46,stroke:#10b981,color:#fff
    classDef mc fill:#1f2937,stroke:#4b5563,color:#fff

    JS["异步沙箱代码执行"]:::js --> Proxy["受控 Proxy 包装器"]:::proxy
    Proxy --> Tx["生成不可变 Action: TeleportAction(playerUUID, pos)"]:::proxy
    Tx --> Buffer["推入 TransactionBuffer 并发队列"]:::q
    Buffer --> Tick["Minecraft Server 主循环 onServerTick()"]:::mc
    Tick --> Commit["主线程原子批处理提交生效!"]:::mc
```

1. **读取数据**：通过预先在主线程快照的只读 DTO 或轻量只读句柄读取，无数据竞争；
2. **写回数据**：任何写操作均序列化为不可变的 `TransactionAction`，推入线程安全的并发队列；
3. **原子提交**：在每个游戏 Tick 固定的时间槽位，由主线程一次性批量消费并应用队列中的 Action，彻底避免并发修改异常（`ConcurrentModificationException`）。

---

## ⚖️ 双容器隔离特性比对表

| 特性维度 | 特权系统容器 (System Context) | 零信任沙箱容器 (Player Sandbox Context) |
| :--- | :--- | :--- |
| **执行载体** | 系统核心服务、ESM Modlets、控制台指令 | 游戏内玩家输入的脚本及衍生异步任务 |
| **宿主访问 (HostAccess)** | `HostAccess.EXPLICIT` (白名单受控访问) | `HostAccess.NONE` (完全禁止访问 Java 原生对象) |
| **Java.type / 反射** | 允许访问经过审计的安全工具类 | **完全切除** (试图访问直接抛 SecurityException) |
| **文件系统权限** | 允许访问 `scripts/` 工作目录受限 I/O | **完全禁止** |
| **执行线程模型** | 主线程同线程或特权异步任务 | **强制在 AsyncWorkerPool 异步线程池执行** |
| **超时抢占机制** | 可配置放宽或关闭 | **强制搭载 50ms 超时 WatchdogTimer** |
| **原型链完整性** | 标准 ECMAScript 原型 | 全局深冻结 (`Object.freeze`)，防原型污染 |

---

## 🔗 相关导航
- 蓝图总索引：[[Index|新一代架构技术蓝图索引]]
- 核心术语：[[Terminology-and-Concepts|核心术语与物理概念体系]]
- 并发与事件：[[Module-Concurrency-and-Events|异步事务缓冲与无锁事件总线]]
- 安全规范：[[Module-Security-Specification|闭包安全令牌与零信任沙箱规范]]
- 模块生态：[[Module-ESM-Modlet-System|标准 ESM 运行时与 Modlet 体系]]
