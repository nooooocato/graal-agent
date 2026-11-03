# Implementation Plan: 第一/二阶段接线闭环、字节码标称类型重构与文档校准 (Foundation Wireup, Nominal Class-File Refactor & Doc Alignment)

## Phase 1: 引导编排与高可用基础设施闭环 (Bootstrap & Telemetry Wireup) [checkpoint: 5cbf381]
- [x] 8478828 Task: 编写 AgentMain 引导编排与异常转储测试 (Red Phase)
    - [x] 编写针对 AgentConfig 与 LibraryInjector 在 Agent 引导链路中的集成测试
    - [x] 编写针对未捕获异常自动触发 CrashDumpRecorder 落盘转储的单元测试
- [x] f808d27 Task: 实现 AgentMain 引导重构与全局异常挂载 (Green Phase)
    - [x] 在 `AgentMain.java` 中接入 `AgentConfig.parse` 并动态配置 `DiagnosticsHub` 级别与日志文件
    - [x] 在 `AgentMain.java` 中接入 `LibraryInjector.injectLibraries` 与依赖门禁探测
    - [x] 在 `AgentMain.java` 顶层 catch 块及全局 `Thread.setDefaultUncaughtExceptionHandler` 中挂载 `CrashDumpRecorder`
- [x] 3f8b810 Task: 清理遗留私有方法与回归验证 (Refactor Phase)
    - [x] 移除 `AgentMain.java` 中废弃的 `loadLibraries`、`resolveScriptsDir` 等私有方法，削减冗余代码
    - [x] 运行 `run-tests.ps1` 验证引导测试通过
- [x] 5cbf381 Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 2: 桥接层反射清零与句柄池双阶段预热 (AgentBridge MH Convergence & Dual-Phase Warmup)
- [x] 13d3466 Task: 编写 AgentBridge 句柄收敛与预热单测 (Red Phase)
    - [x] 编写针对 AgentBridge 通过 MethodHandleCache 调度聊天发送与跨端判断的测试
    - [x] 编写双阶段安全预热机制在实例捕获时的触发测试
- [x] ab47e04 Task: 实现 AgentBridge 裸反射收敛与预热逻辑 (Green Phase)
    - [x] 将 `AgentBridge.sendChatMessage` 中的裸反射与字段缓存重构为 `MethodHandleCache` 句柄调用
    - [x] 将 `AgentBridge.isMultiplayer` 与 `onServerReloadResources` 中的裸反射收敛至句柄池
    - [x] 在 `AgentMain` 与 `EnvironmentContext` 中落地双阶段预热调用
- [ ] Task: 清除 AgentBridge 废弃字段与回归验证 (Refactor Phase)
    - [ ] 彻底清理 `AgentBridge` 内部无用的 `literalMethod`、`sendSystemMessageMethod`、`playerField` 等静态变量
    - [ ] 运行 `run-tests.ps1` 验证桥接调用与指令透传测试通过
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 3: 8 大微插件 Class-File API 标称类型符号化重构 (Nominal Typing Class-File API Refactor)
- [ ] Task: 编写微插件标称类型匹配断言测试 (Red Phase)
    - [ ] 针对 8 大微插件编写验证其 ClassDesc / MethodTypeDesc 符号匹配与字节码生成的专用断言
- [ ] Task: 重构 8 大微插件与 MethodVirtualizer 为标称类型 (Green Phase)
    - [ ] 重构 `BundlerPlugin`：使用 `ClassDesc` 与 `ConstantDescs.CD_String.arrayType()` 匹配 `run` 方法与 `getParent` 指令
    - [ ] 重构 `ClientTickPlugin`：使用 `ClassDesc.of("net.minecraft.client.Minecraft")` 与 `MethodTypeDesc.of(ConstantDescs.CD_void)` 标称匹配
    - [ ] 重构 `KeyboardHandlerPlugin`：消除字符串描述符，使用标称类型匹配 `keyPress(long, int, KeyEvent)`
    - [ ] 重构 `PacketListenerPlugin`：消除 `sendChat`、`sendCommand`、`handleCommands` 原始描述符
    - [ ] 重构 `ServerLifecyclePlugin` 与 `ServerTickPlugin`：使用标称类型匹配生命周期与 tick 方法
    - [ ] 重构 `CommandsInitPlugin` 与 `PackRepositoryPlugin`：消除构造函数与指令集合原始描述符
    - [ ] 统一重构 `MethodVirtualizer` 通用分发桩生成器为纯标称类型 API
- [ ] Task: 运行全量离线单测与 SMV 实机验证总线 (Refactor Phase)
    - [ ] 运行 `run-tests.ps1` 确保 63 个单元测试全部通过
    - [ ] 运行 `verify-smv.ps1` 验证 5 大步实机插件链与字节码校验 100% 通过
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 4: 知识库文档事实与测试矩阵全量校准 (Documentation & Test Matrix Synchronization)
- [ ] Task: 校准核心模块技术规范文档
    - [ ] 校准 `docs/new_base/Module-Diagnostics-and-Logging.md` 中 `CrashDumpRecorder` 实例签名与参数顺序
    - [ ] 校准 `docs/new_base/Module-Bridge-and-Dispatcher.md` 中 `EnvironmentContext` 实例模型描述
    - [ ] 校准 `docs/new_base/Module-Bootstrap.md` 与实际 `AgentMain` 引导流程
- [ ] Task: 校准测试矩阵、真实提交 Hash 与微插件形参
    - [ ] 更新 `docs/new_base/Verification-and-Testing.md` 中 Phase 1 矩阵为真实测试方法名
    - [ ] 将 `docs/new_base/Module-SMV-Mesh.md` 与 `Verification-and-Testing.md` 中的幽灵提交 `79c0da6` 替换为真实提交 `b4934c2`
    - [ ] 修正 `Module-SMV-Mesh.md` 表格中 `KeyboardHandlerPlugin` 的形参签名
    - [ ] 复核所有文档的相对链接与 WikiLinks 连通性
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
