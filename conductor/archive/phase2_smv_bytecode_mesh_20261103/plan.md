# Implementation Plan: 第二阶段：影子方法虚拟化网格与插桩解耦 (SMV Bytecode Mesh & Plugin Pipeline)

## Phase 1: SMV 核心虚拟化器 (MethodVirtualizer)
- [x] Task: 编写 MethodVirtualizer 字节码重命名与转发测试 (Red Phase) (6c66241)
    - [x] 构造样本类并验证生成 `$$shadow$<method>` 私有合成方法
    - [x] 验证原方法槽位成功嵌入分发跳转指令
- [x] Task: 实现 MethodVirtualizer 与分发逻辑 (Green Phase) (4c48161)
    - [x] 创建 `com.graalagent.transformer.smv.MethodVirtualizer`
    - [x] 实现针对无参、带参、带返回值方法的通用分发桩生成器
- [x] Task: 栈映射表与异常表校验 (Refactor Phase) (841d6e8)
    - [x] 验证包含 try-catch 块的方法在虚拟化后字节码校验（Verification）完全合规
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) (8411874)

## Phase 2: 插件契约与调度器 (Pipeline Infrastructure)
- [x] Task: 编写 ClassTransformPlugin 与 SMVOrchestrator 单元测试 (Red Phase) (b3f4cc9)
    - [x] 编写插件基于类名精准路由测试
    - [x] 编写多插件管道连续转换测试
- [x] Task: 实现 SMVOrchestrator 与插件注册表 (Green Phase) (c1073ec)
    - [x] 创建 `com.graalagent.transformer.ClassTransformPlugin` 接口
    - [x] 创建 `com.graalagent.transformer.SMVOrchestrator`
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) (60a16d9)

## Phase 3: 客户端插件集落地 (Client Plugins)
- [x] Task: 编写客户端插件单元测试 (Red Phase) (5def619)
    - [x] 编写 `ClientTickPlugin` 转换测试
    - [x] 编写 `KeyboardHandlerPlugin` 按键阻断与影子透传测试
    - [x] 编写 `PacketListenerPlugin` 聊天与命令拦截测试
- [x] Task: 实现客户端微插件 (Green Phase) (420c6e3)
    - [x] 创建 `com.graalagent.transformer.plugins.ClientTickPlugin`
    - [x] 创建 `com.graalagent.transformer.plugins.KeyboardHandlerPlugin`
    - [x] 创建 `com.graalagent.transformer.plugins.PacketListenerPlugin`
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) (5ba948a)

## Phase 4: 服务端与系统插件集落地 (Server & System Plugins) [checkpoint: a51a900]
- [x] Task: 编写服务端与系统插件单元测试 (Red Phase) (8fdd5ed)
    - [x] 编写 `ServerLifecyclePlugin` 与 `ServerTickPlugin` 测试
    - [x] 编写 `CommandsInitPlugin` 测试
    - [x] 编写 `BundlerPlugin` 与 `PackRepositoryPlugin` 测试
- [x] Task: 实现服务端与系统微插件 (Green Phase) (abd23aa)
    - [x] 创建 `com.graalagent.transformer.plugins.ServerLifecyclePlugin`
    - [x] 创建 `com.graalagent.transformer.plugins.ServerTickPlugin`
    - [x] 创建 `com.graalagent.transformer.plugins.CommandsInitPlugin`
    - [x] 创建 `com.graalagent.transformer.plugins.BundlerPlugin`
    - [x] 创建 `com.graalagent.transformer.plugins.PackRepositoryPlugin`
- [x] Task: 废弃并移除旧版巨石类 `MinecraftTransformer` (Refactor Phase) (b4934c2)
    - [x] 将 `AgentBootstrap` 中的转换器注册更新为 `SMVOrchestrator`
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) (a51a900)

## Phase: Review Fixes
- [x] Task: Apply review suggestions (27fe1bf)
