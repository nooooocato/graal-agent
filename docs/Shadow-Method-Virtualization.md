---
title: 影子方法虚拟化与融合规范 (Shadow Method Virtualization)
date: 2026-11-02
tags:
  - "#/bytecode"
  - "#/architecture"
  - "#/compatibility"
---

# 影子方法虚拟化与融合规范 (Shadow Method Virtualization)

## 1. 背景与核心痛点

在传统的 Minecraft 模组生态中，多个 ModLoader（Fabric 与 NeoForge）或多个重量级模组经常会对同一个原版关键方法（例如 `Player.hurt(...)` 或 `MinecraftServer.tickChildren(...)`）进行激进修改。
- **痛点一：Mixin 碰撞闪退 (Overwrite Collision)：** 当两个模组试图用 `@Overwrite` 替换同一个方法体时，Mixin 处理器无法仲裁，直接抛出致命异常导致游戏崩溃。
- **痛点二：修改被后到者吞并：** 顺序靠后的模组可能完全覆盖前者的逻辑，导致模组功能神出鬼没地失效。
- **痛点三：跨 Loader 无法共存：** Fabric 的 Mixin 和 NeoForge 的 ASM CoreMod 各自为战，根本无法在同一个类加载器上下文中共享并合并修改。

[[Terminology#Shadow Method Virtualization (影子方法虚拟化)|影子方法虚拟化]] 彻底解决了这些痛点：**保持堆内存游戏对象唯一，但将不同生态的代码视点分离为影子方法，由 Agent 主路由统一仲裁调度。**

---

## 2. 字节码分流物理模型

利用 Java 25 原生的 `java.lang.classfile`（Class-File API），当一个目标类（如 `net.minecraft.world.entity.player.Player`）被加载并写入 JVM `defineClass` 时，Agent 执行以下重构操作：

```
                      ┌──────────────────────────────────────┐
                      │    Player.class (全局唯一堆内存类型)    │
                      ├──────────────────────────────────────┤
                      │                                      │
                      │ 1. hurt$vanilla(DamageSource, float) │ <-- 纯净原版代码镜像
                      │                                      │
                      │ 2. hurt$fabric(DamageSource, float)  │ <-- Fabric Mixin 修改镜像
                      │                                      │
                      │ 3. hurt$neoforge(DamageSource, float)│ <-- NeoForge ASM 修改镜像
                      │                                      │
                      │ 4. hurt(DamageSource, float) [主路由] │ <-- Agent 动态合成的中枢
                      │    ├── a. Agent 动态脚本切片前置      │
                      │    ├── b. 仲裁流水线调用影子方法      │
                      │    └── c. Agent 动态脚本切片后置      │
                      └──────────────────────────────────────┘
```

---

## 3. 主流水线调度器代码范式 (Master Pipeline Router)

以 `Player.hurt(DamageSource source, float amount) -> boolean` 为例，Agent 动态合成的主方法结构如下：

```java
public boolean hurt(DamageSource source, float amount) {
    // ==========================================
    // 阶段 1: Agent 动态切片前置拦截 (Java / JS)
    // ==========================================
    HurtContext ctx = new HurtContext(this, source, amount);
    if (ArbitrationEngine.triggerPreHurt(ctx)) {
        return ctx.isCancelled(); // 被用户脚本直接取消
    }

    // ==========================================
    // 阶段 2: 影子方法流水线传递与执行
    // ==========================================
    boolean fabricExecuted = false;
    boolean neoforgeExecuted = false;
    float currentAmount = ctx.getAmount();

    // 2.1 执行 Fabric 影子方法 (若存在)
    try {
        if (hasShadowMethod("hurt$fabric")) {
            currentAmount = this.hurt$fabric(source, currentAmount);
            fabricExecuted = true;
        }
    } catch (Throwable t) {
        Logger.error("Fabric 影子方法 [hurt$fabric] 执行异常，自动隔离该模组逻辑", t);
    }

    // 2.2 执行 NeoForge 影子方法 (若存在，入参为上一阶段处理后的数据)
    try {
        if (hasShadowMethod("hurt$neoforge")) {
            currentAmount = this.hurt$neoforge(source, currentAmount);
            neoforgeExecuted = true;
        }
    } catch (Throwable t) {
        Logger.error("NeoForge 影子方法 [hurt$neoforge] 执行异常，自动隔离该模组逻辑", t);
    }

    // ==========================================
    // 阶段 3: 冲突仲裁与优雅兜底 (Arbitration Fallback)
    // ==========================================
    boolean result;
    if (ArbitrationEngine.hasCustomRule("Player.hurt")) {
        // 用户编写了自定义 Java/JS 仲裁规则
        result = ArbitrationEngine.resolve("Player.hurt", this, source, currentAmount);
    } else {
        // 未配置仲裁规则：采用弹性非崩溃式兜底
        // 传递给原版干净内核完成最终状态结算
        result = this.hurt$vanilla(source, currentAmount);
        if (fabricExecuted && neoforgeExecuted) {
            Logger.logToFileOnly("[Arbitration Notice] Player.hurt 同时检测到多 Loader 修改，已按流水线安全合并");
        }
    }

    // ==========================================
    // 阶段 4: Agent 动态切片后置通知
    // ==========================================
    ArbitrationEngine.triggerPostHurt(this, source, currentAmount, result);

    return result;
}
```

---

## 4. 冲突仲裁决策矩阵 (Collision Arbitration Matrix)

当多方修改产生不兼容的返回值或副作用时，仲裁引擎遵循以下优先级规则：

| 场景 | 仲裁机制 | 处理动作 | 是否闪退 | 日志级别 |
| :--- | :--- | :--- | :---: | :--- |
| **已定义脚本规则** | 用户指定规则 (`mcpack.json` 或 Java) | 执行用户定制的判定函数 | 否 | INFO |
| **数值型状态变化** | 顺序流水线传递 (Pipeline) | 将上游修改结果作为下游输入继续演算 | 否 | DEBUG |
| **互斥布尔返回值** | 默认安全兜底 | 保留最后生效模组的修改；或回退原版 | 否 | WARN |
| **第三方影子抛异常** | 沙箱隔离断路器 (Circuit Breaker) | 阻断异常影子方法，继续执行原版兜底 | 否 | ERROR (附完整堆栈) |

> [!important] 铁律：零闪退设计
> 传统的模组冲突会直接弹出崩溃弹窗退出游戏；Agent 的影子方法体系**永远优先保证游戏进程存活**，以“记录告警 + 安全降级”换取不间断的游戏体验。

---

## 5. 基于 Java 25 Class-File API 的实现关键点

1. **避免 ASM 依赖锁定：**
   全面使用 JDK 25 的 `java.lang.classfile.ClassFile`。无论第三方 ModLoader 使用 ASM 7、ASM 9 还是其他工具链，输入给 Agent 的都是标准 class 字节流。
2. **栈图重算 (StackMapTable)：**
   合成影子方法和主调度方法会引入新的跳转分支（`Label`）和异常处理表。Java 25 `ClassFile` 的 `StackMapOption.STACK_MAPS_WHEN_PATCHED` 会自动重新计算并验证 StackMap，保证 JVM 严格校验通过。
3. **保留调用上下文：**
   影子方法继承原方法的所有访问修饰符和泛型签名，避免因签名变更导致第三方模组反射或 MethodHandle 查找失败。

---

## 关联导航
- 核心术语体系：[[Terminology|核心术语与概念体系]]
- 系统全景架构：[[Architecture|系统全景架构]]
- 多 Loader 兼容设计：[[Multi-Loader-Compatibility|多 ModLoader 兼容与字节码协调]]
- 双引擎混编：[[Dual-Engine-Runtime|双引擎运行时与动态编译]]
- 实施路线与规划：[[Roadmap-and-Tracks|实施路线图与 Track 拆解]]
