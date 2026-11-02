# Implementation Plan: 第五阶段：ESM Modlet 模块生态与虚拟内存数据包 (ESM Modlet System & Virtual In-Memory Datapack)

## Phase 1: GraalVM ESM 运行时与路径解析器 (ESM Engine)
- [ ] Task: 编写 ESM import/export 模块加载与相对路径解析测试 (Red Phase)
    - [ ] 验证跨文件 ESM 模块成功加载执行
    - [ ] 验证试图超出 scripts 根目录的 import 请求被安全拒绝
- [ ] Task: 实现 ESM SourceProvider 与安全模块加载器 (Green Phase)
    - [ ] 在 ContextManager 中启用 ESM 模式配置
    - [ ] 实现自定义模块解析器与定位策略
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 2: Modlet 模块包体系与生命周期 (Modlet Management)
- [ ] Task: 编写 manifest.json 解析、拓扑排序与生命周期测试 (Red Phase)
    - [ ] 测试依赖拓扑排序与循环依赖检测
    - [ ] 测试 `onEnable` 与 `onDisable` 触发顺序
- [ ] Task: 实现 ModletManager 与 ModletContext (Green Phase)
    - [ ] 创建 `com.graalagent.modlet.ModletManager`
    - [ ] 创建专属于 Modlet 的隔离上下文 `ModletContext` (独立的定时器、指令表)
    - [ ] 实现单 Modlet 增量重载算法
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 3: 虚拟内存数据包引擎 (VirtualPackResources)
- [ ] Task: 编写 VirtualPackResources 内存数据流测试 (Red Phase)
    - [ ] 验证向内存插入 JSON 能够通过原版 `PackResources.getResource` 接口读回流
    - [ ] 验证内存虚拟 `pack.mcmeta` 生成合规性
- [ ] Task: 实现 VirtualPackResources 与 VirtualRepositorySource (Green Phase)
    - [ ] 创建 `com.graalagent.datapack.VirtualPackResources` 实现原版接口
    - [ ] 创建 `com.graalagent.datapack.VirtualRepositorySource`
- [ ] Task: 接入 PackRepositoryPlugin 字节码插桩 (Refactor Phase)
    - [ ] 将虚拟数据源注入 Minecraft 原版资源管理器
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 4: 面向 JS 的声明式 Datapack DSL (Script API Bindings)
- [ ] Task: 编写 Datapack DSL 脚本交互与动态刷新测试 (Red Phase)
    - [ ] 验证 JS 代码调用 `addRecipe` 成功更新内存资源表
- [ ] Task: 实现 Datapack DSL 绑定与热重载驱动 (Green Phase)
    - [ ] 向全局/Modlet 上下文注入 `agent.datapack` 对象
    - [ ] 联动 `server.reloadResources()` 驱动原版服务热重载
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
