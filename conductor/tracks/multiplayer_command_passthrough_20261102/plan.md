# Implementation Plan: Multiplayer Command Passthrough, Dynamic Suggestion Sync & Security Sandboxing (`multiplayer_command_passthrough`)

## Phase 1: 服务端控制台无参指令与生命周期修复 [checkpoint: a2eca4d]
- [x] Task: 编写服务端指令无参执行与重载解耦单元测试 (Red Phase) ba37cc4
    - [x] 编写测试用例验证无参调用 `serverinfo`、`fstest` 的语法树解析
    - [x] 编写测试用例验证执行 `/reloadjs` 后再调用自定义指令不报上下文已关闭
- [x] Task: 修复 ServerCommandDispatcher 指令注册与动态回调解析 (Green Phase) e0f92ab
    - [x] 在 `customLiteral` 节点上增加 `executesLiteral.invoke(customLiteral, customCommand)` 支持无参调用
    - [x] 改造 `handleCustomExecution` 为执行时动态获取 `ScriptManager.getInstance().getCustomCommands().get(cmdName)`
    - [x] 修复 `EnvironmentContext.broadcastMessage` 中加载 `Component` 的类加载器
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) a2eca4d

## Phase 2: 客户端指令穿透与提示动态同步 [checkpoint: c1513e7]
- [x] Task: 编写多人游戏指令穿透与过滤测试 (Red Phase) 51fd487
    - [x] 编写测试验证在多人模式下 `/serverinfo`、`/js`、`/reloadjs` 返回 false（穿透发包）
    - [x] 编写测试验证 `/cjs`、`/clientjs` 在客户端本地拦截并求值
- [x] Task: 实现客户端指令穿透与本地专属调试指令 (Green Phase) 792514c
    - [x] 在 `AgentBridge.isMultiplayer()` 中接入 `Minecraft.getInstance().isLocalServer()` 检测
    - [x] 改造 `ScriptManager.onSendCommand`：若处于多人模式，将指令穿透至服务端；仅拦截处理 `/cjs`
- [x] Task: 改造指令提示同步逻辑 (Green Phase) c1513e7
    - [x] 调整 `ClientPacketListener.handleCommands`：多人模式下不注入本地指令，完全跟随服务端网络包
    - [x] 在服务端热重载或指令注册时，调用 `server.getPlayerList().getPlayers().forEach(p -> server.getCommands().sendCommands(p))` 动态推送更新
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) c1513e7

## Phase 3: 多人环境客户端穿透安全沙箱 [checkpoint: 164e92c]
- [x] Task: 编写客户端玩家执行 JS 的安全沙箱单元测试 (Red Phase) d654a89
    - [x] 编写测试验证玩家执行 `Java.type(...)` 抛出安全异常
    - [x] 编写测试验证玩家执行 `agent.fs.writeFile(...)` 抛出权限拒绝
    - [x] 编写测试验证控制台来源依然具备完整执行权限
- [x] Task: 实现执行来源识别与降权沙箱引擎 (Green Phase) 164e92c
    - [x] 在 `ServerCommandDispatcher` 中区分指令来源（控制台 vs 游戏内玩家）
    - [x] 在 `ScriptManager` 中为玩家穿透执行提供受限环境（屏蔽 `Java` 全局对象，禁用写文件操作）
- [x] Task: Phase Verification & Checkpoint (Refer to workflow.md) 164e92c

## Phase 4: 原版 /reload 与 /reloadjs 包含关系重构
- [ ] Task: 编写重载层级关系单元测试 (Red Phase)
    - [ ] 编写测试验证 `/reloadjs` 仅重载 JS，不重载数据包
    - [ ] 编写测试验证原版 `reloadResources` 触发时自动联动重载 JS
- [ ] Task: 实现 MinecraftServer.reloadResources 插桩联动 (Green Phase)
    - [ ] 在 `MinecraftTransformer` 中对 `MinecraftServer.reloadResources` 进行字节码插桩，在完成时触发 `AgentBridge.onServerReloadResources()`
    - [ ] 调整 `ScriptManager.reload()` 逻辑，使 `/reloadjs` 专精于 JS 重载与客户端提示同步
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 5: 全链路构建与端到端回归验收
- [ ] Task: 自动化测试全量回归与覆盖率检查
    - [ ] 运行 `run-tests.ps1` 确保全部单元测试 100% 通过
- [ ] Task: 执行 `build.ps1` 打包最新 `mc-agent.jar`
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
