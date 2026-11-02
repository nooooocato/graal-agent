---
title: 双引擎运行时与动态编译规范 (Dual-Engine Runtime & In-Memory Compiler)
date: 2026-11-02
tags:
  - "#/runtime"
  - "#/compiler"
  - "#/polyglot"
  - "#/hot-reload"
---

# 双引擎运行时与动态编译规范 (Dual-Engine Runtime & In-Memory Compiler)

## 1. 架构定位：为什么要双引擎？

在 Minecraft 高级定制与模组开发中，单一脚本语言往往面临两难绝境：
- **纯动态语言（JavaScript / Python）：** 语法敏捷、交互性强，但面对高频 Tick（每秒 20 次处理成千上万个实体）、复杂的 3D 向量矩阵运算或字节码切片织入时，解释器开销与反射封包会导致明显的性能抖动（Lag Spike）。
- **纯静态预编译语言（常规 Java）：** 性能极高（原生 JIT 编译），但开发流程痛苦——修改一行代码需要退出游戏、重新运行 Gradle 编译打包、重启客户端/服务端等待 2 分钟，严重打击迭代效率。

`graal-agent` 的 **双引擎混编体系** 实现了二者的完美融合：

| 引擎 | 语言与技术栈 | 职责分工 | 性能表现 | 热重载方式 |
| :--- | :--- | :--- | :---: | :--- |
| **底层动力引擎** | **Java 25 (JSR 199 内存编译)** | 高性能 Hook、实体高频 Tick、复杂算法、字节码仲裁 | 原生 JIT，零反射开销 | `PackageClassLoader` 卸载 + 动态重载 |
| **表层敏捷引擎** | **GraalJS (ES2024+ Polyglot)** | 玩法事件监听、动态交互指令、NPC 对话、UI 绑定 | 极速解释执行 | 上下文热置换与事件重绑定 |

---

## 2. 动态 Java 25 内存即时编译实现 (In-Memory JavaCompiler)

系统内置标准 `javax.tools.JavaCompiler`，无需依赖宿主机安装外部构建工具（如 Maven/Gradle），直接在 JVM 内存中完成编译：

```mermaid
flowchart LR
    A["packages/&lt;id&gt;/src/*.java 源码文件"] --> B["WatchService 变动监听"]
    B --> C["JSR 199 内存文件管理器 (In-Memory JavaFileManager)"]
    C --> D["Java 25 原生编译器 (javax.tools.JavaCompiler)"]
    D --> E["生成内存字节码 byte[] (零磁盘写入)"]
    E --> F["注入全新 PackageClassLoader"]
    F --> G["实例化并绑定全局事件总线"]
```

### 关键性能指标：
- **秒级/毫秒级反馈：** 50 个复杂 Java 类的内存编译全过程仅需 **60~120ms**。
- **内存零污染：** 源码直接在堆内存中转译为字节码流，不产生临时的 `.class` 垃圾文件。

---

## 3. PackageClassLoader 生命周期与内存垃圾回收机制

在讨论热重载时，开发者最常担忧的问题是：**“频繁重载 Java 类会不会导致 JVM 元空间 (Metaspace) 内存碎片或泄漏？”**

答案是：**只要 Agent 框架主动斩断外部引用，现代 JVM（GraalVM JDK 25）能够 100% 自动全额回收！**

### 卸载与回收四步法：
1. **解除生命周期契约：** 微包重新加载前，Agent 自动调用旧实例的 `onDisable()`，注销其在 `EventBus` 和 `CommandDispatcher` 上的所有监听器。
2. **切断类加载器引用：** 将指向旧 `PackageClassLoader` 的引用置为 `null`。
3. **元空间垃圾回收 (Metaspace GC)：** 当 JVM 判定该 `PackageClassLoader` 及其加载的所有 `Class<?>` 对象均无强引用时，G1/ZGC 垃圾回收器会自动释放元空间中占用的本地操作系统内存。
4. **代码缓存清理 (CodeCache Sweeper)：** 针对通过 `Instrumentation.retransformClasses` 热替换的方法体，JVM 的 JIT 代码缓存器会自动清理陈旧的机器码段落。

---

## 4. 双引擎双向互通协议 (Bidirectional Interop)

Java 与 JavaScript 不是割裂的孤岛，它们在内存中通过共享上下文实现无缝调用：

### 4.1 JavaScript 调用动态编译的 Java 类
动态编译生成的 Java 类自动注册在微包命名空间中，JS 中可直接无缝实例化或调用静态工具：
```javascript
// 在 scripts/index.js 中直接使用内存编译的 Java 类
const SpellMath = Java.type('com.myplugin.magic.SpellMath');
const damage = SpellMath.calculateDamage(player, target);
```

### 4.2 动态 Java 调用 JavaScript 业务闭包
Java 胶水代码可以向 JavaScript 派发事件，或直接获取 JS 定义的高阶函数：
```java
// 在 src/CombatArbitrator.java 中触发 JS 侧的玩法监听
AgentAPI.getEventBus().emit("customMagicDamage", targetPlayer, finalDamage);
```

---

## 关联导航
- 核心术语体系：[[Terminology|核心术语与概念体系]]
- 系统全景架构：[[Architecture|系统全景架构]]
- 模块化微包规范：[[Package-System|graalpack 模块化微包规范]]
- 影子方法融合：[[Shadow-Method-Virtualization|影子方法虚拟化与融合规范]]
- 实施路线与规划：[[Roadmap-and-Tracks|实施路线图与 Track 拆解]]
