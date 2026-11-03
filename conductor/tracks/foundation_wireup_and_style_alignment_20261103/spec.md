# Specification: 第一/二阶段接线闭环、字节码标称类型重构与文档校准 (Foundation Wireup, Nominal Class-File Refactor & Doc Alignment)

## Overview
本 Track 旨在闭环已归档的 **第一阶段（基础设施）** 与 **第二阶段（SMV 字节码网格）** 中交付的孤岛组件，彻底消除调用端的代码遗留债务，并响应《Java 代码规范与工程标准》（`conductor/code_styleguides/java.md`）中的标称类型优先（Nominal Typing First）标准，将所有微插件的字节码编织逻辑从原始字符串描述符重构为强类型 Class-File API 符号体系，同时同步校准 6 处知识库文档事实。

---

## Functional Requirements

### 1. `AgentMain` 引导流程重构与组件接入
- 接入 `AgentConfig.parse`，接管 `-javaagent:graal-agent.jar=<args>` 参数解析，完整支持 `lib=`、`scripts=`、`log=` 与 `debug=true|false`；
- 接入 `DiagnosticsHub.getInstance().initFile(config.logFilePath().toFile())`，并根据 `config.debugMode()` 动态设置最小日志级别为 `DEBUG`；
- 接入 `LibraryInjector.injectLibraries(config.libDir(), inst)`，执行依赖 JAR 字典序升序挂载，并自动触发 `DEFAULT_CRITICAL_CLASSES` 关键类探测门禁；
- 接入 `SMVOrchestrator.createDefault()` 注册字节码网格；
- 彻底废除并删除 `AgentMain` 内旧版私有方法 `loadLibraries`、`resolveScriptsDir` 等 ~100 行遗留重复代码。

### 2. 生产崩溃现场快照转储与全局监听器挂载
- 在 `AgentMain.startAgent` 的顶层 `catch (Throwable t)` 异常块中，调用 `new CrashDumpRecorder().recordCrash(t, "Agent Premain Startup Failure", Map.of())`；
- 注册 `Thread.setDefaultUncaughtExceptionHandler`，当未捕获异常源自 `com.graalagent` 时自动落盘 `dumps/agent-crash-<timestamp>.dump`，保障生产运行时故障现场的高可观测性。

### 3. `AgentBridge` 裸反射收敛至 `MethodHandleCache` 与双阶段预热
- 全面移除 `AgentBridge` 内部维护的裸反射变量（`Method literalMethod`、`Method sendSystemMessageMethod`、`Field playerField` 等）；
- 将 `sendChatMessage`、`isMultiplayer`、`onServerReloadResources` 的反射调用全面收敛至 `MethodHandleCache` 统一管理与 JIT 内联；
- 实施双阶段安全预热机制：
  1. **第一阶段（引导期）**：在 `AgentMain` 启动完成时触发 JVM / 基础依赖句柄静态预热；
  2. **第二阶段（捕获期）**：在 `EnvironmentContext` 首次捕获客户端（`Minecraft`）或服务端（`MinecraftServer`）实例时触发 MC 专用句柄预热（`Component.literal`、`sendSystemMessage` 等）。

### 4. 8 大微插件 Class-File API 标称类型符号化改造 (Nominal Typing Alignment)
- 遵循 `conductor/code_styleguides/java.md` 第 5 节规范：
  - **严禁原始字符串描述符**：彻底消除 `([Ljava/lang/String;)V`、`()V`、`()Ljava/lang/ClassLoader;`、`Ljava/lang/String;` 等手写描述符；
  - **类型符号化**：全面使用 `ConstantDescs`（`CD_String`, `CD_int`, `CD_void`, `CD_long`, `CD_boolean` 等）与 `ClassDesc.of("net.minecraft...")` 点分隔标称类型；
  - **方法签名符号化**：使用 `MethodTypeDesc.of(...)` 构造签名，严禁 `MethodTypeDesc.ofDescriptor(...)`；
  - **标称类型模式匹配**：使用 `mm.methodTypeSymbol().equals(...)`、`mm.methodName().equalsString(...)`、`inv.owner().asSymbol().equals(...)`、`inv.typeSymbol().equals(...)` 替代原生字符串比对；
- 改造覆盖范围：
  - `BundlerPlugin`
  - `ClientTickPlugin`
  - `KeyboardHandlerPlugin`
  - `PacketListenerPlugin`
  - `ServerLifecyclePlugin`
  - `ServerTickPlugin`
  - `CommandsInitPlugin`
  - `PackRepositoryPlugin`
  - `MethodVirtualizer` 通用分发桩与调用辅助方法

### 5. 知识库文档与测试矩阵全量校准
- **校准 `Module-Diagnostics-and-Logging.md`**：修正 `CrashDumpRecorder` 为实例 API，更正方法名为 `formatDump`，校准参数顺序；
- **校准 `Module-Bridge-and-Dispatcher.md`**：修正 `EnvironmentContext` 为上下文实例模型及 `getLiveContext()` 门面；
- **校准 `Verification-and-Testing.md`**：修正第一阶段测试表格中虚构的方法名，更新为代码中的真实测试方法名；
- **校准真实 Commit Hash**：将 `Module-SMV-Mesh.md`、`Verification-and-Testing.md` 及历史计划中的幽灵提交 `79c0da6` 统一修正为真实提交 `b4934c2`；
- **校准微插件参数**：修正 `Module-SMV-Mesh.md` 表格中 `KeyboardHandlerPlugin` 的形参签名；
- **校准引导拓扑说明**：对齐 `Module-Bootstrap.md` 与实际 `AgentMain` 编排。

---

## Non-Functional Requirements
- **测试通过率**：全量单元测试（`run-tests.ps1`）与实机验证总线（`verify-smv.ps1`）100% 通过；
- **代码整洁度**：`AgentMain.java` 代码行数缩减 50% 以上；微插件零裸字符串描述符；
- **平台合规性**：严格契合 Java 25 / Oracle GraalVM 与 JDK 24+ Class-File API 标准。

---

## Acceptance Criteria
- [ ] `AgentMain` 成功由 `AgentConfig` 与 `LibraryInjector` 驱动，命令行 `debug=true` 与 `log=` 经测试有效；
- [ ] 抛出未捕获异常时自动触发 `CrashDumpRecorder` 生成转储文件；
- [ ] `AgentBridge` 内部无任何裸 `java.lang.reflect.Method` 缓存字段，全量收敛至 `MethodHandleCache`；
- [ ] 8 大微插件与 `MethodVirtualizer` 均改用 `ClassDesc` / `MethodTypeDesc` 标称类型，`verify-smv.ps1` 5 步全量通过；
- [ ] 文档审查指出的 6 项事实偏差全部对齐校准完毕。

---

## Out of Scope
- 第三阶段的读写锁并发与 Watchdog 50ms 超时熔断引擎（由 `phase3` 轨道实施）；
- 第四阶段的双容器沙箱与原型链递归冻结（由 `phase4` 轨道实施）。
