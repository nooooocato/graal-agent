---
title: mc-agent 架构与工程 Wiki 知识库
date: 2026-11-02
tags:
  - "#/index"
  - "#/wiki"
  - "#/overview"
---

# `mc-agent` 架构与工程 Wiki 知识库

欢迎查阅 `mc-agent`（Minecraft 26.3 Universal Meta-Loader & 双引擎运行时底座）的官方技术规范与架构 Wiki。

本项目采用 **Wiki Flavored Markdown** 编写，所有概念、规范与模块均通过 `[[Wikilinks]]` 深度互联，支持在 Obsidian 中以网状知识图谱形式检阅，亦便于在命令行和各类 AI Agent 协作流中高效导航与持续维护。

---

## 🗺️ 核心知识地图 (Navigation Sitemap)

```
                            [[Index|Wiki 知识库首页]]
                                      │
            ┌─────────────────────────┼─────────────────────────┐
            ▼                         ▼                         ▼
  [[Terminology|核心术语表]]      [[Architecture|系统全景架构]]    [[Roadmap-and-Tracks|实施路线与Track拆解]]
                                      │
       ┌──────────────────────────────┼──────────────────────────────┐
       ▼                              ▼                              ▼
[[Shadow-Method-Virtualization|影子方法虚拟化规范]]  [[Package-System|mcpack 模块化微包规范]]  [[Dual-Engine-Runtime|双引擎与动态编译规范]]
                                      │
                                      ▼
                   [[Multi-Loader-Compatibility|多 Loader 兼容与字节码协调]]
```

---

## 📚 文档导读与索引

### 1. 核心理论与架构基石
- **[[Architecture|系统全景架构 (System Architecture)]]**
  深入剖析系统四层架构模型、类加载器（ClassLoader）隔离拓扑与 JVM Premain 顶层接管机制。
- **[[Terminology|核心术语与概念体系 (Terminology & Concepts)]]**
  定义了影子方法、语义化锚点、DAG 拓扑、JSR 199 内存编译等所有专有技术概念。

### 2. 关键技术规范
- **[[Shadow-Method-Virtualization|影子方法虚拟化与融合规范 (Shadow Method Virtualization)]]**
  解决多 Loader（Fabric / NeoForge）在单一堆内存对象上同时修改方法的“圣杯级”方案，定义了 Master Pipeline Router 与非崩溃式优雅仲裁。
- **[[Package-System|mcpack 模块化微包规范与包管理器 (Package System Specification)]]**
  规范了 `packages/<id>/` 目录标准、`mcpack.json` Schema、依赖 DAG 拓扑排序算法与两阶段演进。
- **[[Dual-Engine-Runtime|双引擎运行时与动态编译规范 (Dual-Engine Runtime & In-Memory Compiler)]]**
  基于 Java 25 原生 `javax.tools.JavaCompiler` 实现的内存编译管道、毫秒级响应、PackageClassLoader 垃圾回收机制与 GraalJS 双向互通。
- **[[Multi-Loader-Compatibility|多 ModLoader 兼容与字节码协调 (Multi-Loader Compatibility & Weaving)]]**
  分析 KnotClassLoader 和 TransformingClassLoader 的字节码管线，采用语义化锚点技术实现与第三方 Mixin 的和平共存。

### 3. 工程落地与实施
- **[[Roadmap-and-Tracks|实施路线图与 Track 拆解建议 (Roadmap & Track Breakdown)]]**
  将宏大架构拆解为 5 个独立、自治、高确定性的 Conductor Tracks，保障 AI Agent 在后续研发中具备最高的工作精度和效率。

---

## 🛠️ 知识库演化与维护准则
1. **持续同步：** 当某个 Track 实现发生架构偏移或修正时，必须同步更新对应的 Wiki 文档，并记录修改日期与关联提交。
2. **术语一致：** 新增功能或代码重构时，统一遵循 [[Terminology|核心术语表]] 的定义命名类和接口。
3. **双向链接：** 新增文档时，请务必使用 `[[Wikilinks]]` 与现有知识节点连接，维持网状图谱的连通性。
