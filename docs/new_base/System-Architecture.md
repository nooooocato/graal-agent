---
title: 系统分层拓扑与微内核架构 (System Architecture)
date: 2026-11-03
tags:
  - "#/architecture"
  - "#/topology"
  - "#/microkernel"
  - "#/sequence-diagram"
  - "#/new-base"
---

# 系统分层拓扑与微内核架构 (System Architecture)

新一代 Graal-Agent 架构完全摒弃前代原型中 `ScriptManager` 与 `MinecraftTransformer` 的巨石结构，全面转向**基于事件驱动与插件管线的“微内核 + 服务门面 (Microkernel + Service Facade)”分层拓扑体系**。

---

## 🏗️ 全局分层拓扑图

```mermaid
flowchart TD
    classDef l1 fill:#0f172a,stroke:#38bdf8,color:#fff
    classDef l2 fill:#1e1b4b,stroke:#818cf8,color:#fff
    classDef l3 fill:#064e3b,stroke:#34d399,color:#fff
    classDef l4 fill:#701a75,stroke:#f472b6,color:#fff
    classDef l5 fill:#7c2d12,stroke:#fb923c,color:#fff

    subgraph L1["第一层: 引导与类加载脱壳 (Bootstrap Core) - [已交付 ✅]"]
        BM["AgentMain (premain / agentmain 入口)"]:::l1
        AC["AgentConfig (强类型只读配置 Record)"]:::l1
        LL["LibraryInjector (System ClassLoader 字典序挂载)"]:::l1
        BF["BundlerPlugin (URLClassLoader Parent 脱壳修复)"]:::l1
    end

    subgraph L2["第二层: 字节码虚拟化网格 (SMV Mesh Pipeline) - [已交付 ✅]"]
        SMV["SMVOrchestrator (JDK 24+ Class-File API 调度网格)"]:::l2
        MV["MethodVirtualizer (影子方法克隆与分发桩重写)"]:::l2
        PL["8 大微插件流水线 (Client / Server / System Mesh)"]:::l2
    end

    subgraph L3["第三层: 桥接分发与指令网关 (Native Bridge & Gateway) - [已交付 ✅]"]
        AB["AgentBridge (静态钩子分发门面)"]:::l3
        EC["EnvironmentContext (双端抽象 / 主线程安全调度)"]:::l3
        MHC["MethodHandleCache (方法句柄池 / 纳秒级预热)"]:::l3
        BA["BrigadierAdapter (统一流式指令网关 / 全员网络推送)"]:::l3
        DH["DiagnosticsHub (多通道异步遥测 / 崩溃快照落盘)"]:::l3
    end

    subgraph L4["第四层: 双容器引擎与并发内核 (Polyglot Dual-Engine & Concurrency)"]
        SEF["ScriptEngineFacade (统一脚本门面)"]:::l4
        SYS["System Context (常驻特权环境)"]:::l4
        SBX["Player Sandbox Context Pool (零信任临时隔离池)"]:::l4
        WD["WatchdogTimer (超时 50ms 抢占中断)"]:::l4
        WP["AsyncWorkerPool (专用虚拟线程池)"]:::l4
    end

    subgraph L5["第五层: 模块化生态与原生资源 (Ecosystem & Resource Pipeline)"]
        ML["ModletManager (ESM 加载器 / manifest.json)"]:::l5
        EB["LockFreeEventBus (无锁并发事件总线)"]:::l5
        TB["TransactionBuffer (主线程事务安全同步)"]:::l5
        VDP["VirtualDatapackEngine (内存/物理双模包)"]:::l5
    end

    BM --> AC
    AC --> LL
    LL --> BF
    BF --> SMV
    SMV --> MV
    MV --> PL
    PL --> AB
    AB --> EC
    AB --> MHC
    AB --> BA
    AB --> DH
    AB --> SEF
    SEF --> SYS
    SEF --> SBX
    SBX --> WD
    SEF --> WP
    SEF --> ML
    SEF --> EB
    SEF --> TB
    SEF --> VDP
```

---

## ⚙️ 核心分层设计与协作契约

### 1. 引导与脱壳层 (Bootstrap Core) - [已交付 ✅]
- **职责**：作为 Agent 的最初入口点（`premain` / `agentmain`），承担类路径探测、依赖动态追加与关键类门禁。
- **解耦关键点**：由 [`AgentConfig.java`](../../src/com/graalagent/bootstrap/AgentConfig.java) 与 [`LibraryInjector.java`](../../src/com/graalagent/bootstrap/LibraryInjector.java) 承载；微插件 [`BundlerPlugin.java`](../../src/com/graalagent/transformer/plugins/BundlerPlugin.java) 消除 Bundler 对类加载器的人工截断。详见 [[Module-Bootstrap]]。

### 2. 字节码虚拟化网格层 (SMV Mesh) - [已交付 ✅]
- **职责**：采用 [[Module-SMV-Mesh|影子方法虚拟化 (SMV)]]，将游戏目标方法重命名保留为私有合成方法（`$$shadow$(name)`），原方法槽位替换为虚拟分发桩。
- **解耦关键点**：由 [`MethodVirtualizer.java`](../../src/com/graalagent/transformer/smv/MethodVirtualizer.java) 与 [`SMVOrchestrator.java`](../../src/com/graalagent/transformer/SMVOrchestrator.java) 调度 8 个独立微插件，彻底废除 402 行巨石单体类。

### 3. 桥接分发与指令网关层 (Bridge & Gateway) - [已交付 ✅]
- **职责**：向底层字节码提供极低开销的静态转发调用，连接 Minecraft 原生对象与 Agent 上层组件。
- **解耦关键点**：
  - 引入 [`MethodHandleCache.java`](../../src/com/graalagent/bridge/MethodHandleCache.java)，以预热的方法句柄替换低效反射，单次调用提速 91.3%；
  - 抽取 [`BrigadierAdapter.java`](../../src/com/graalagent/bridge/brigadier/BrigadierAdapter.java)，统一指令树构建与全员热推送；
  - 引入 [`DiagnosticsHub.java`](../../src/com/graalagent/diagnostics/DiagnosticsHub.java) 实现非阻塞异步日志落盘与崩溃快照转储。详见 [[Module-Bridge-and-Dispatcher]] 与 [[Module-Diagnostics-and-Logging]]。

### 4. 双容器引擎与并发内核层 (Polyglot & Concurrency)
- **职责**：管理 GraalVM Polyglot 运行时与多任务并发。
- **解耦关键点**：
  - **特权 System Context**：常驻运行核心系统服务与控制台指令；
  - **零信任 Player Sandbox Context**：隔离执行游戏玩家指令，切断全部 Java 反射；
  - **异步 Worker 线程池 + 看门狗**：将玩家指令异步化，结合超时看门狗（`context.interrupt(Duration.ZERO)`），彻底终结死锁主线程的历史难题。详见 [[Module-DualContext-Engine]]。

### 5. 模块化生态与原生资源层 (Ecosystem & Resources)
- **职责**：面向 JS 脚本开发者提供符合 Web 标准的模块化开发体验。
- **解耦关键点**：
  - 引入符合 ES2024 规范的标准 ESM 模块导入与 [[Module-ESM-Modlet-System|Modlet 包管理]]；
  - 引入 [[Module-Virtual-Datapack|虚拟内存数据包引擎]]，实现零磁盘 I/O 的配方与战利品表动态秒级挂载；
  - 引入 [[Module-Concurrency-and-Events|主线程事务缓冲队列 (TransactionBuffer)]]，将脚本对游戏世界的修改打包为不可变原子事务排队切回主线程生效。

---

## 🔄 系统全流程运行时序图 (End-to-End Sequence)

```mermaid
sequenceDiagram
    autonumber
    actor Player as 玩家 / 控制台
    participant MC as Minecraft 主循环
    participant Hook as SMV 虚拟分发桩
    participant Bridge as AgentBridge
    participant Router as CommandRouter
    participant Worker as AsyncWorkerPool
    participant Sandbox as Player Sandbox
    participant WD as WatchdogTimer
    participant Buffer as TransactionBuffer

    Note over Player,MC: 玩家在游戏中输入带有潜在死循环的代码
    Player->>MC: /js while(true) { ... }
    MC->>Hook: 进入命令拦截桩
    Hook->>Bridge: 路由至 BrigadierAdapter
    Bridge->>Router: handleCommand(context)

    alt 来源是控制台
        Router->>Router: 在 System Context 直接同步/异步执行
    else 来源是普通/管理员玩家
        Router->>Worker: 提交异步任务 (携带 SecurityContext 闭包令牌)
        Worker->>WD: 启动看门狗计时器 (设定 50ms 超时)
        Worker->>Sandbox: 执行 eval(code)
        
        Note over Sandbox,WD: 脚本陷入死循环 while(true)
        WD->>WD: 超过 50ms 预算未结束!
        WD->>Sandbox: 调用 context.interrupt(Duration.ZERO) 强行熔断!
        Sandbox-->>Worker: 抛出 PolyglotException (Execution interrupted)
        Worker-->>Bridge: 捕获超时异常，格式化错误输出
        Bridge-->>Player: 聊天栏提示: §c[安全熔断] 脚本执行超时 (超过 50ms 限制)
    end

    Note over MC,Buffer: 正常情况下的世界写操作 (如 player.give)
    opt 脚本包含合法的世界修改调用
        Sandbox->>Buffer: 记录不可变事务 Action (give item)
        Note over MC: 下一 Tick 开始
        MC->>Hook: onServerTick()
        Hook->>Buffer: flushTransactions(server)
        Buffer->>MC: 在 Minecraft 主线程安全应用修改!
    end
```

---

## 🔗 相关导航
- 蓝图总索引：[[Index|新一代架构技术蓝图索引]]
- 核心术语：[[Terminology-and-Concepts|核心术语与物理概念体系]]
- 架构愿景：[[Vision-and-Principles|架构愿景与设计哲学]]
- 验证套件：[[Verification-and-Testing|验证与基准测试套件体系]]
- 迁移计划：[[Refactoring-and-Migration-Plan|演进路线与重构落地实施计划]]
