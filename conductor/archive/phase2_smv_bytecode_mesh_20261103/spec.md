# Specification: 第二阶段：影子方法虚拟化网格与插桩解耦 (SMV Bytecode Mesh & Plugin Pipeline)

## Overview
本 Track 落实新一代架构技术蓝图第二阶段（参见 [[new_base/Refactoring-and-Migration-Plan|演进路线与重构落地实施计划]]），旨在废除原有的 400 行巨石单体 `MinecraftTransformer`，正式落地 **[[Shadow-Method-Virtualization|影子方法虚拟化 (SMV)]]** 架构。

主要解决前代实现中：
1. 直接在原方法体内部拼接汇编指令（`cb.invokestatic`, `cb.ifeq`, `cb.return_`）导致栈映射表不稳定、原版调用栈破坏的问题；
2. 单文件汇集多个异构类的插桩逻辑，严重违背单一职责原则与开闭原则的问题。

详细设计规范参照：
- [[new_base/Module-SMV-Mesh|影子方法虚拟化网格规范]]

---

## Functional Requirements

### 1. 核心虚拟化器 (`MethodVirtualizer`)
- 基于 JDK 24+ 原生 `java.lang.classfile` API 构建；
- 支持将类中的任何目标方法克隆并重命名为 `private synthetic $$shadow$<name>`，完整保留原方法的所有指令、局部变量表与异常表；
- 将原方法槽位替换为虚拟分发桩（Virtual Dispatcher），支持嵌入前置（Prefix）、环绕（Around）、替换（Replace）与后置（Postfix）回调逻辑。

### 2. 插件化网格调度器 (`SMVOrchestrator` & `ClassTransformPlugin`)
- 定义统一的插件契约接口 `ClassTransformPlugin`：
  - `String getTargetClassName()`
  - `void transform(ClassModel classModel, ClassBuilder classBuilder)`
- 实现调度器 `SMVOrchestrator`，基于目标类名哈希表快速分发，彻底淘汰级联 `if-else`。

### 3. 微内核插件集拆分
将旧版插桩逻辑独立拆分为 8 个标准化微插件：
1. `ClientTickPlugin`：虚拟化 `Minecraft.tick()`；
2. `KeyboardHandlerPlugin`：虚拟化 `KeyboardHandler.keyPress(...)`，支持消费阻断并按需执行 `$$shadow$keyPress`；
3. `PacketListenerPlugin`：虚拟化 `ClientPacketListener.sendChat` / `sendCommand`；
4. `ServerLifecyclePlugin`：拦截 `MinecraftServer.runServer()` 的启动与停机生命周期；
5. `ServerTickPlugin`：拦截 `MinecraftServer.tickServer()`；
6. `CommandsInitPlugin`：拦截 `Commands.<init>()` 并在返回前注入 Brigadier 调度器；
7. `BundlerPlugin`：负责 URLClassLoader 父委托解耦；
8. `PackRepositoryPlugin`：负责合成数据包仓库源注入。

---

## Non-Functional Requirements
- **稳定性**：未被钩子拦截的方法调用，其性能开销应仅等同于一次常规私有方法调用（被 JIT 完全内联）；
- **向后兼容**：所有原版游戏类的公开签名保持 100% 相同。

---

## Acceptance Criteria
- [ ] 针对 `MethodVirtualizer` 的离线 Mock 类插桩单测通过，验证原方法被成功转为影子私有方法且原方法正常分发；
- [ ] 8 个微插件均有对应单元测试覆盖其匹配逻辑与字节码变换流程；
- [ ] `SMVOrchestrator` 成功注册至 `Instrumentation`，废弃并移除旧版单体类 `MinecraftTransformer.java`。
