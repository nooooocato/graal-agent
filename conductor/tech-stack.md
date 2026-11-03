# Technology Stack: Minecraft 26.3 JavaScript Agent (`graal-agent`)

## 1. Core Runtime & Language
- **Language / Version:** Java 25
- **JDK Distribution:** Oracle GraalVM JDK 25.0.4+7.1 (Windows x64)
- **Class Format:** Class Version 69 (Java 25)

## 2. Scripting & Polyglot Engine
- **Engine:** Oracle GraalJS 25.0.4
- **Language Specification:** Modern JavaScript (ES2024+)
- **Runtime Libraries:**
  - `polyglot-25.0.4.jar`
  - `truffle-api-25.0.4.jar`
  - `truffle-runtime-25.0.4.jar`
  - `js-language-25.0.4.jar`
  - `icu4j-25.0.4.jar`
  - `regex-25.0.4.jar`
- **Dynamic Loading:** Injected dynamically into system classloader at runtime via `LibraryInjector` and strongly typed bootstrap config (`AgentConfig`).

## 3. Bytecode Manipulation & Game Integration
- **Instrumentation Mechanism:** Java Instrumentation API (`premain` / `agentmain`).
- **Bytecode Framework:** Native `java.lang.classfile` (Java 25 Class-File API, zero third-party dependencies).
- **Architecture Pattern:** [[Shadow-Method-Virtualization|Shadow Method Virtualization (SMV)]] & Micro-Plugin Mesh Pipeline:
  - `MethodVirtualizer`: Method body cloning to `private synthetic $$shadow$<name>`, virtual dispatcher stubs (Prefix, Around, Replace, Postfix) with JIT inlining and stack-map safety.
  - `SMVOrchestrator`: Hash-routed class transformation pipeline replacing monolithic transformers.
  - Micro-Plugins: `ClientTickPlugin`, `KeyboardHandlerPlugin`, `PacketListenerPlugin`, `ServerLifecyclePlugin`, `ServerTickPlugin`, `CommandsInitPlugin`, `BundlerPlugin`, `PackRepositoryPlugin`.
- **Target Platform:** Minecraft 26.3 (Mojang mappings, Client & Dedicated Server).
- **Command Framework:** Minecraft native Brigadier command dispatcher with `BrigadierAdapter` (Fluent DSL, unified client completion push & server tree registration).
- **Reflection & Invocation:** `MethodHandleCache` (cached `MethodHandle` resolution and static warmup for high-frequency game methods).

## 4. Concurrency, Diagnostics & File System
- **Concurrency Architecture:** `ConcurrencyController` (`ReentrantReadWriteLock` 分离每刻超高频非阻塞读锁与低频热重载排他写锁，配合 `contextLock` 严格保障 GraalJS 容器单线程串行化，杜绝 `Multi threaded access requested`)。
- **Async Workers & Watchdog Interruption:** `AsyncWorkerPool`（异步工作线程池）剥离耗时计算与玩家 `/js` 求值；`WatchdogTimer`（50ms 时限看门狗抢占令牌）针对 `while(true){}` 死循环强制调用 `context.interrupt(Duration.ZERO)` / `context.close(true)` 强行熔断，保持游戏 20.0 TPS。
- **Main-Thread Transaction Buffer:** `TransactionBuffer`（MPSC 无锁并发队列）收集异步线程的游戏世界写操作，在 `onServerTick()` 阶段原子批量消费，配置 `MAX_BATCH_PER_TICK = 1000` 截断防掉帧，彻底消除 `ConcurrentModificationException`。
- **Lock-Free Event Bus:** `LockFreeEventBus`（基于 `CopyOnWriteArrayList` 的无锁并发事件总线），支持 6 级事件优先级 (`HIGHEST -> MONITOR`) 与 `Cancellable` 短路机制。
- **Thread Safety:** 状态变更统一路由至主线程心跳与 `TransactionBuffer`，辅助以 `AgentBridge.executeOnGameThread(...)`。
- **Diagnostics & Logging:** `DiagnosticsHub` (asynchronous ring buffer, dynamic Log4j 2 / SLF4J binding, ANSI color formatting) and `CrashDumpRecorder` (`dumps/*.dump` automated crash snapshots).
- **File System Monitoring:** Java NIO `WatchService` monitoring `scripts/` directory for live hot reloading.
- **Data & Asset Management:** Java NIO Path / UTF-8 File APIs enabling scripts to generate datapacks, virtual models, and configs.

## 5. Build & Development Tools
- **Build Script:** PowerShell 7 (`build.ps1`).
- **Compilation Toolchain:** `javac` (UTF-8 encoded) + `jar` manifest packaging.
- **Output Artifact:** `graal-agent.jar` (single agent JAR containing manifest and core classes).
