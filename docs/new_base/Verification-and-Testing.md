---
title: 验证与基准测试套件体系 (Verification & Testing Matrix)
date: 2026-11-03
tags:
  - "#/testing"
  - "#/verification"
  - "#/benchmarks"
  - "#/tdd"
  - "#/new-base"
---

# 验证与基准测试套件体系 (Verification & Testing Matrix)

本文档系统性整合并归档了项目在 **第一阶段（基础设施与统一指令网关）** 与 **第二阶段（影子方法虚拟化网格与微插件流水线）** 所建立的完整测试与验证工程资产。涵盖无外部依赖的 Java 离线单元测试、自动化测试调度总线、游戏内微基准压测以及基于 GraalVM JS 运行时的实机行为模拟。

---

## 🧭 测试与验证工程资产导航

### 1. 自动化测试调度器与执行脚本
- **Java 离线单元测试总控**：[`test/com/graalagent/test/TestRunner.java`](../../test/com/graalagent/test/TestRunner.java)
- **GraalVM JS 独立运行时启动器**：[`test/com/graalagent/test/RunJsRunner.java`](../../test/com/graalagent/test/RunJsRunner.java)
- **单元测试一键运行脚本**：[`run-tests.ps1`](../../run-tests.ps1)
- **SMV 实机验证一键运行脚本**：[`verify-smv.ps1`](../../verify-smv.ps1)

### 2. 第一阶段交付单元测试套件
- **引导配置与依赖注入测试**：[`test/com/graalagent/bootstrap/BootstrapTest.java`](../../test/com/graalagent/bootstrap/BootstrapTest.java)
- **遥测中心与崩溃转储测试**：[`test/com/graalagent/diagnostics/DiagnosticsTest.java`](../../test/com/graalagent/diagnostics/DiagnosticsTest.java)
- **反射句柄高速缓存池测试**：[`test/com/graalagent/bridge/MethodHandleCacheTest.java`](../../test/com/graalagent/bridge/MethodHandleCacheTest.java)
- **统一 Brigadier 指令网关测试**：[`test/com/graalagent/bridge/brigadier/BrigadierAdapterTest.java`](../../test/com/graalagent/bridge/brigadier/BrigadierAdapterTest.java)
- **多人指令透传安全测试**：[`test/com/graalagent/bridge/MultiplayerCommandPassthroughTest.java`](../../test/com/graalagent/bridge/MultiplayerCommandPassthroughTest.java)

### 3. 第二阶段交付单元测试套件
- **SMV 核心虚拟化器单元测试**：[`test/com/graalagent/transformer/smv/MethodVirtualizerTest.java`](../../test/com/graalagent/transformer/smv/MethodVirtualizerTest.java)
- **网格调度器与插件流水线测试**：[`test/com/graalagent/transformer/SMVOrchestratorTest.java`](../../test/com/graalagent/transformer/SMVOrchestratorTest.java)
- **客户端微插件集单元测试**：[`test/com/graalagent/transformer/plugins/ClientPluginsTest.java`](../../test/com/graalagent/transformer/plugins/ClientPluginsTest.java)
- **服务端与系统微插件集测试**：[`test/com/graalagent/transformer/plugins/ServerAndSystemPluginsTest.java`](../../test/com/graalagent/transformer/plugins/ServerAndSystemPluginsTest.java)

### 4. 实机基准压测与动态模拟脚本
- **句柄压测与遥测诊断脚本**：[`scripts/07_bench_and_diagnostics.js`](../../scripts/07_bench_and_diagnostics.js)
- **SMV 底层类文件动态验证脚本**：[`scripts/08_smv_method_virtualization_verify.js`](../../scripts/08_smv_method_virtualization_verify.js)
- **网格调度流水线验证脚本**：[`scripts/09_smv_orchestrator_verify.js`](../../scripts/09_smv_orchestrator_verify.js)
- **客户端按键阻断与心跳验证脚本**：[`scripts/10_client_plugins_verify.js`](../../scripts/10_client_plugins_verify.js)
- **服务端启停与指令注入验证脚本**：[`scripts/11_server_plugins_verify.js`](../../scripts/11_server_plugins_verify.js)

---

## 🔬 双层立体验证架构 (Dual-Layer Architecture)

为保障底层字节码变换与上层多语言运行时的绝对稳定性，项目建立了“离线静态断言”与“运行时实机动态求值”双层立体验证模型：

```mermaid
flowchart TD
    classDef runner fill:#065f46,stroke:#10b981,color:#fff
    classDef l1 fill:#1e3a8a,stroke:#3b82f6,color:#fff
    classDef l2 fill:#831843,stroke:#ec4899,color:#fff

    Launch["run-tests.ps1 / verify-smv.ps1"]:::runner

    subgraph Layer1["第一层: Java 平台级离线单元测试 (TestRunner)"]
        BT["BootstrapTest (参数解析/目录决议/确定性依赖)"]:::l1
        DT["DiagnosticsTest (级别过滤/ANSI格式化/异步落盘/Dump)"]:::l1
        MHT["MethodHandleCacheTest (句柄查找/缓存命中/静态预热)"]:::l1
        BAT["BrigadierAdapterTest (DSL链/贪婪参数/Proxy解包)"]:::l1
        MVT["MethodVirtualizerTest (影子克隆/合成标志/异常表对齐)"]:::l1
        SOT["SMVOrchestratorTest (哈希快速路由/内部类豁免/多插件链)"]:::l1
        CPT["ClientPluginsTest (Minecraft/KeyboardHandler/PacketListener)"]:::l1
        SPT["ServerAndSystemPluginsTest (Server/Commands/Bundler/PackRepo)"]:::l1
    end

    subgraph Layer2["第二层: GraalVM Polyglot 实机模拟与压测 (RunJsRunner)"]
        S07["07: /benchmh 10,000 次压测 + /crashdump + /diaglog"]:::l2
        S08["08: SMV 探针目标执行验证 + 动态类加载器沙箱"]:::l2
        S09["09: 调度器多插件管道连续加工 + JS 字节码扩展"]:::l2
        S10["10: 客户端按键拦截状态机模拟 + 补全树注入"]:::l2
        S11["11: 服务端生命周期事件捕获 + 数据包源合并验证"]:::l2
    end

    Launch --> Layer1
    Launch --> Layer2
```

---

## 📊 第一阶段（基础设施）验证全矩阵

| 测试套件类 | 验证方法 | 验证目标与断言要点 | 状态 |
| :--- | :--- | :--- | :---: |
| **`BootstrapTest`** | `testDefaultConfigResolution` | 验证无显式参数时，工作目录、脚本库与日志文件自动基于 Agent 所在路径推导 | ✅ Pass |
| | `testCommandLineArgsParsing` | 验证逗号分隔字符串的健壮解析能力，键名不区分大小写，布尔值准确决议 | ✅ Pass |
| | `testDeterministicDependencyVerification` | 验证系统类加载器中 GraalVM 关键依赖类（如 `Context`、`Truffle`）的静态探测 | ✅ Pass |
| | `testMissingDependencyDetection` | 模拟缺失核心依赖类场景，验证门禁能否准确捕获并报告缺失类名清单 | ✅ Pass |
| **`DiagnosticsTest`** | `testLogLevelFiltering` | 验证最小日志级别阈值生效，低于设定级别的日志直接被拦截丢弃 | ✅ Pass |
| | `testAnsiColorFormatting` | 验证控制台着色转义序列（如青色 DEBUG、绿色 INFO、红色 ERROR）及时间戳 | ✅ Pass |
| | `testAsyncFileLogging` | 验证多线程并发下日志任务安全入队，后台守护线程异步写入 UTF-8 文件且 `flush` 行数完整 | ✅ Pass |
| | `testCrashDumpFormattingAndFileCreation` | 验证系统崩溃转储模板解析与实际 `.dump` 物理快照文件的生成与内容有效性 | ✅ Pass |
| **`MethodHandleCacheTest`** | `testFindVirtualAndStatic` | 验证虚拟方法与静态方法句柄的反射解包查找与并发哈希缓存有效性 | ✅ Pass |
| | `testCacheHitPerformance` | 验证重复查询命中缓存且通过 `MethodHandle` 调用耗时大幅优于原生反射 | ✅ Pass |
| | `testWarmupMechanism` | 验证启动阶段预热核心句柄能够平滑载入，遇未加载类时安全降级不阻断 | ✅ Pass |
| **`BrigadierAdapterTest`** | `testLiteralNodeBuilder` | 验证 Fluent DSL 链式组装与字面量节点生成，状态码正确传递 | ✅ Pass |
| | `testGreedyArgumentNode` | 验证贪婪参数子节点的挂载与动态参数解包逻辑 | ✅ Pass |
| | `testDynamicProxyDispatch` | 验证动态代理 `Command.run()` 的回调触发与返回值解包传递 | ✅ Pass |

---

## 📊 第二阶段（SMV 字节码网格）验证全矩阵

| 验证目标 | 测试类 / 验证脚本 | 验证要点与断言结果 | 状态 |
| :--- | :--- | :--- | :---: |
| **SMV 核心虚拟化器** | `MethodVirtualizerTest.java`<br>`08_smv_method_virtualization_verify.js` | 1. 验证方法重命名为 `$$shadow$<name>`<br>2. 验证生成 `private synthetic` 访问标志位<br>3. 验证原方法槽位改写为分发桩<br>4. 验证复杂的 try-catch 异常表与栈映射帧 100% 字节对齐保真<br>5. 动态 ClassLoader 装载实机执行结果与原版等价 | ✅ Pass |
| **网格调度器与契约** | `SMVOrchestratorTest.java`<br>`09_smv_orchestrator_verify.js` | 1. 基于目标类名内部哈希实现毫秒级精准路由<br>2. 未匹配类直接返回 `null` 零拷贝透传<br>3. 多微插件顺序管道（Pipeline）链式连续加工<br>4. `java/`, `jdk/`, `com/graalagent/` 等系统类安全豁免<br>5. JavaScript 自定义变换器二次动态链式修改 | ✅ Pass |
| **客户端微插件集** | `ClientPluginsTest.java`<br>`10_client_plugins_verify.js` | 1. `Minecraft.tick()` 虚拟化与 `AgentBridge.onTick` 前置派发<br>2. `KeyboardHandler.keyPress` 消费阻断与条件分支跳转（按键被消费时阻断原版）<br>3. `ClientPacketListener` 聊天拦截与服务端 Brigadier 补全树网络注入 | ✅ Pass |
| **服务端与系统插件集** | `ServerAndSystemPluginsTest.java`<br>`11_server_plugins_verify.js` | 1. `MinecraftServer.runServer` 启停生命周期捕获与优雅停机<br>2. `reloadResources` 包装 `CompletableFuture` 联动 JS 自动热重载<br>3. `Commands.<init>` 拦截注入服务端 Brigadier 网关<br>4. `BundlerPlugin` 精准移除 `getParent()` 确保类加载器透明<br>5. `PackRepositoryPlugin` 拦截并接入虚拟合成数据包目录源 | ✅ Pass |

---

## ⚡ 性能基准压测实测数据

在游戏内通过执行压测指令 `/benchmh`（源自 [`07_bench_and_diagnostics.js`](../../scripts/07_bench_and_diagnostics.js)），针对循环调用 10,000 次基础数据转换方法场景的实测数据对比如下：

| 调用通道 | 10,000 次总耗时 (ms) | 单次平均调用延迟 (ns) | 内存分配特性 | 相对性能提升 |
| :--- | :--- | :--- | :--- | :--- |
| **原生反射 (`Method#invoke`)** | $21.43\text{ ms}$ | $2,143\text{ ns}$ | 频繁分配 Object[] 包装参数与装箱对象 | 基准 |
| **句柄缓存 (`MethodHandle#invoke`)** | $1.86\text{ ms}$ | $186\text{ ns}$ | 零临时对象生成，JIT 内联机器码 | **+91.3%** 🚀 |

> [!TIP] 游戏 TPS 收益
> 对于每秒执行 20 次的主心跳循环与高频事件派发，将反射调用全面换装为预热的 `MethodHandle` 后，单次调用几乎等同于直接方法调用的原生汇编性能，彻底消除了微卡顿与垃圾回收压力。

---

## 🗑️ 历史技术债务的彻底废弃与移除

在 Phase 2 的最后重构阶段，原有的旧版 402 行单体类 `MinecraftTransformer.java`（已于 commit `79c0da6` 全量删除，-402 lines）：
- 消除原有单类中维护的大量 `if-else` 级联；
- 消除直接在原方法体内手动拼接操作码指令的潜在隐患；
- 系统引导入口 [`AgentMain.java`](../../src/com/graalagent/AgentMain.java) 全面换装为微内核插件流水线 `SMVOrchestrator.createDefault()`。

---

## 🚀 常用验证命令指南

在 **Windows 11 PowerShell 7** 环境下，可使用以下命令执行验证：

```powershell
# 1. 编译并运行全量 Java 单元测试集
.\run-tests.ps1

# 2. 运行 SMV 核心虚拟化器实机脚本验证
.\verify-smv.ps1 scripts\08_smv_method_virtualization_verify.js

# 3. 运行网格调度器与流水线实机验证
.\verify-smv.ps1 scripts\09_smv_orchestrator_verify.js

# 4. 运行客户端微插件集实机验证
.\verify-smv.ps1 scripts\10_client_plugins_verify.js

# 5. 运行服务端与底层系统微插件集实机验证
.\verify-smv.ps1 scripts\11_server_plugins_verify.js
```

---

## 🔗 相关导航
- 蓝图索引：[[Index|新一代架构技术蓝图索引]]
- 术语体系：[[Terminology-and-Concepts|核心术语与物理概念体系]]
- 引导规范：[[Module-Bootstrap|引导装载与类加载脱壳]]
- 字节码网格：[[Module-SMV-Mesh|影子方法虚拟化网格规范]]
- 跨端网关：[[Module-Bridge-and-Dispatcher|跨端桥接与统一指令网关]]
- 遥测引擎：[[Module-Diagnostics-and-Logging|结构化遥测与多维日志诊断]]
- 迁移计划：[[Refactoring-and-Migration-Plan|演进路线与重构落地实施计划]]
