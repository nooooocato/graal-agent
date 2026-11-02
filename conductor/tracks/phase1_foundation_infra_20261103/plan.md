# Implementation Plan: 第一阶段：基础设施重构与统一指令网关 (Foundation Infrastructure & Command Gateway)

## Phase 1: 结构化遥测与崩溃转储引擎 (Diagnostics & Logging)
- [x] Task: 编写 DiagnosticsHub 与 CrashDumpRecorder 单元测试 (Red Phase) e8eaa54
    - [x] 编写日志级别过滤与 ANSI 着色单元测试
    - [x] 编写异步队列落盘与崩溃快照生成单元测试
- [ ] Task: 实现 DiagnosticsHub 与 CrashDumpRecorder (Green Phase)
    - [ ] 创建 `com.graalagent.diagnostics.DiagnosticsHub` 与日志级别枚举
    - [ ] 实现动态探测绑定 Log4j 2 的适配逻辑
    - [ ] 实现 `com.graalagent.diagnostics.CrashDumpRecorder` 环境与栈快照抓取
- [ ] Task: 重构与覆盖率校验 (Refactor Phase)
    - [ ] 统一现有旧 `Logger` 的方法委托至 `DiagnosticsHub`
    - [ ] 确保测试覆盖率超过 80%
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 2: 反射句柄高速缓存池 (MethodHandleCache)
- [ ] Task: 编写 MethodHandleCache 单元测试 (Red Phase)
    - [ ] 编写针对类方法动态预解析与缓存命中率测试
    - [ ] 编写基于句柄的 invokeExact 调用性能测试
- [ ] Task: 实现 MethodHandleCache 与预热逻辑 (Green Phase)
    - [ ] 创建 `com.graalagent.bridge.MethodHandleCache`
    - [ ] 建立针对 `Component.literal`、`isSameThread`、`execute` 的预热方法
- [ ] Task: 整合与回退测试 (Refactor Phase)
    - [ ] 在 `EnvironmentContext` 中用句柄替换裸反射调用
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 3: 统一 Brigadier 指令网关 (BrigadierAdapter)
- [ ] Task: 编写 BrigadierAdapter 节点构建与动态代理测试 (Red Phase)
    - [ ] 编写 Fluent DSL 链式构建测试 (字面量、贪婪字符串参数)
    - [ ] 编写动态代理回调触发测试
- [ ] Task: 实现 BrigadierAdapter 与全员推送逻辑 (Green Phase)
    - [ ] 创建 `com.graalagent.bridge.brigadier.BrigadierAdapter`
    - [ ] 实现 `NodeBuilder` 与统一反射 Proxy 绑定
    - [ ] 实现 `syncCommandsToAll(server)` 广播方法
- [ ] Task: 代码去重与收敛 (Refactor Phase)
    - [ ] 替换 `ServerCommandDispatcher` 与 `ScriptManager` 中的旧版硬编码反射
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 4: 强类型引导配置与依赖加载器 (Bootstrap & Config)
- [ ] Task: 编写 AgentConfig 与 LibraryInjector 单元测试 (Red Phase)
    - [ ] 编写命令行参数解析测试
    - [ ] 编写确定性依赖检验测试
- [ ] Task: 实现 AgentConfig 与 LibraryInjector (Green Phase)
    - [ ] 创建 `com.graalagent.bootstrap.AgentConfig` record
    - [ ] 创建 `com.graalagent.bootstrap.LibraryInjector`
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
