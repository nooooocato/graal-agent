# Specification: 第一阶段：基础设施重构与统一指令网关 (Foundation Infrastructure & Command Gateway)

## Overview
本 Track 落实新一代架构技术蓝图第一阶段（参见 [docs/new_base/Refactoring-and-Migration-Plan.md](../../../docs/new_base/Refactoring-and-Migration-Plan.md)），旨在重构 JavaAgent 的引导体系与底层通信网关。
主要解决前代实现中：
1. `EnvironmentContext`、`AgentBridge` 和 `ServerCommandDispatcher` 频繁触发低效裸反射的问题；
2. 客户端补全树与服务端指令注册重复硬编码 30 余行 Brigadier 代理反射的问题；
3. 日志缺乏结构化与崩溃排查转储能力的问题；
4. 引导参数解析缺乏类型安全校验的问题。

详细设计规范参照：
- [Module-Bootstrap.md](../../../docs/new_base/Module-Bootstrap.md)
- [Module-Bridge-and-Dispatcher.md](../../../docs/new_base/Module-Bridge-and-Dispatcher.md)
- [Module-Diagnostics-and-Logging.md](../../../docs/new_base/Module-Diagnostics-and-Logging.md)

---

## Functional Requirements

### 1. 反射句柄高速缓存池 (`MethodHandleCache`)
- 集中管理 Minecraft 26+ 核心类的 `MethodHandle` 句柄（如 `Component.literal`, `isSameThread`, `execute`, `sendSystemMessage`, `getPlayerList`, `broadcastSystemMessage`）；
- 提供在 Agent 引导期或首次类加载时的静态预热（`warmup`）机制；
- 对高频调用的方法提供比原生反射快 90% 以上的句柄调用通道。

### 2. 统一 Brigadier 指令网关 (`BrigadierAdapter`)
- 提供流畅的 Fluent DSL 构建 Brigadier 指令节点：`BrigadierAdapter.literal(name).requires(predicate).thenGreedy(argName, callback).registerTo(dispatcher)`；
- 统一客户端补全树注入（单人与多人环境隔离）与服务端真实指令树注册，彻底消除双端重复代理代码；
- 提供全员指令同步接口 `syncCommandsToAll(server)`，实现热重载后客户端补全树的秒级网络推送。

### 3. 多通道结构化日志与崩溃转储 (`DiagnosticsHub` & `CrashDumpRecorder`)
- 提供异步日志缓冲流，避免控制台 I/O 阻塞主循环；
- 动态探测并无缝桥接 Minecraft 官方 Log4j 2 / SLF4J 日志系统；
- 记录结构化安全审计日志与终端 ANSI 着色；
- 实现 `CrashDumpRecorder`：在发生严重未捕获异常时自动输出包含环境信息、字节码插桩状态及活动模块的转储快照（`dumps/agent-crash-<timestamp>.dump`）。

### 4. 强类型配置解析与依赖加载解耦 (`AgentConfig` & `LibraryInjector`)
- 将 `AgentMain` 内部的路径拼接剥离为不可变记录类 `AgentConfig`；
- `LibraryInjector` 负责扫描挂载 GraalVM 依赖至 System ClassLoader，并执行确定性关键类验证（如 `org.graalvm.polyglot.Context`）。

---

## Non-Functional Requirements
- **性能**：`MethodHandle` 句柄单次调用延迟 $\le 5\text{ ns}$；
- **兼容性**：在 Log4j 未加载前平滑降级为终端输出与文件追加，绝不产生早起死锁；
- **测试覆盖率**：核心工具包单元测试覆盖率 $\ge 80\%$。

---

## Acceptance Criteria
- [ ] 针对 `MethodHandleCache` 的单元测试通过，成功通过句柄调用预热方法；
- [ ] 针对 `BrigadierAdapter` 的模拟测试通过，成功组装字面量与贪婪参数节点；
- [ ] 模拟触发严重异常，验证 `CrashDumpRecorder` 能够正常输出格式化的 `.dump` 文件；
- [ ] `AgentConfig` 能正确解析逗号分隔的参数（如 `scripts=path,lib=path,debug=true`）。
