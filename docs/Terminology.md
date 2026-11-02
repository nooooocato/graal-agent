---
title: 核心术语与概念体系 (Terminology & Concepts)
date: 2026-11-02
tags:
  - "#/terminology"
  - "#/architecture"
  - "#/core"
---

# 核心术语与概念体系 (Terminology & Concepts)

本文档定义了 `mc-agent` 项目在向 **Universal Meta-Loader（全能元加载器）** 与 **双引擎混编系统** 演进过程中的核心概念、专有术语和物理模型。

---

## 1. 架构与加载层级术语

### Universal Meta-Loader (全能元加载器)
运行在 JVM `-javaagent` 层级的主管引擎（Supervisor）。早于任何游戏主类与第三方 ModLoader（如 Fabric `KnotClient`、NeoForge `ModLauncher`）执行。负责接管依赖图扫描、动态编排类加载器、执行字节码融合与全局生命周期仲裁。
参见：[[Architecture|系统全景架构]]

### JVM Premain Bootstrap (JVM 前置引导)
Java 提供的底层机制（`java.lang.instrument`）。允许 Agent 在任何业务代码执行前获取 `Instrumentation` 实例，向全局注册 `ClassFileTransformer`，拥有在类字节码加载入内存前的绝对拦截权。

### Dual-Engine Hybrid (双引擎混编)
结合 **Java 25 内存即时编译 (JSR 199)** 与 **GraalJS (ES2024+)** 的混合编程模型。
- **动态 Java 胶水：** 负责高性能 Hook、高频 Tick 运算、复杂数据结构与字节码仲裁。
- **动态 JS 玩法：** 负责敏捷事件监听、交互式指令、NPC 对话、UI 绑定与热更新调试。
参见：[[Dual-Engine-Runtime|双引擎运行时与动态编译]]

---

## 2. 字节码与多 Loader 融合术语

### Shadow Method Virtualization (影子方法虚拟化)
为了在单一份堆内存游戏对象（如 `Player` 实例）上同时运行多个不同 ModLoader（Fabric / NeoForge）修改的逻辑，将各方对同一原版方法的改动隔离并重命名为独立的“影子方法”（如 `hurt$fabric`、`hurt$neoforge`、`hurt$vanilla`），并在主方法生成聚合调度路由。
参见：[[Shadow-Method-Virtualization|影子方法虚拟化与融合规范]]

### Master Pipeline Router (主流水线调度路由)
由 Agent 自动织入原始方法体的控制中枢。负责按照设定规则（前置脚本切片 -> 管道流水线传递 -> 原版兜底 -> 后置脚本切片）调度各个影子方法，并处理返回值合并。

### Non-Fatal Collision Arbitration (非崩溃式冲突仲裁)
当多个 ModLoader 或脚本对同一状态产生逻辑互斥时，Agent 不中断游戏进程，而是按照“脚本自定义仲裁 -> 保留后到者状态 -> 回退原版状态”的阶梯策略进行安全降级，并输出结构化警告日志。

### Semantic Anchoring (语义化锚点)
基于 Java 25 原生 `java.lang.classfile` 的模式匹配织入技术。不依赖脆弱的指令绝对行号或字节码偏移量，而是匹配方法结构中的语义标记（如方法入口 `HEAD`、返回指令 `ReturnInstruction`、或特定方法调用 `InvokeInstruction`），确保在经历多次第三方 Mixin 篡改后依然稳定植入。
参见：[[Multi-Loader-Compatibility|多 ModLoader 兼容与字节码协调]]

---

## 3. 模块化包管理术语

### mcpack (模块化微包)
项目的原子分发单元。每个微包是一个自包含的独立目录（或归档），包含 `mcpack.json` 清单文件，能够同时封装 Java 源码、JS 脚本、数据包、资源包，并声明对外部 Fabric/NeoForge 模组的依赖。
参见：[[Package-System|mcpack 模块化微包规范]]

### Dependency DAG (依赖有向无环图)
包管理器在加载微包前构建的拓扑依赖关系图。用于检测循环依赖（Circular Dependency）、解析包加载优先级，并在启动时验证所需外部 ModLoader 与环境前置是否就绪。

---

## 4. 内存与热重载术语

### PackageClassLoader (微包隔离类加载器)
每个微包独占的子类加载器。用于加载该微包内由 JSR 199 内存编译生成的 Java 字节码。当微包发生热更新时，旧加载器被解除所有引用并交由 JVM Metaspace GC 自动回收，新加载器实现无重启秒级替换。

### Retransform Classes (动态类重织入)
调用 JVM `Instrumentation.retransformClasses()` 接口，在运行时对已加载的原版类重新应用 `ClassFileTransformer`。用于实现动态字节码切片修改的即时生效，无需重启客户端或服务端。

---

## 关联导航
- 回到首页：[[Index|文档首页]]
- 核心系统架构：[[Architecture|系统全景架构]]
- 影子方法融合：[[Shadow-Method-Virtualization|影子方法虚拟化与融合规范]]
- 实施路线与规划：[[Roadmap-and-Tracks|实施路线图与 Track 拆解]]
