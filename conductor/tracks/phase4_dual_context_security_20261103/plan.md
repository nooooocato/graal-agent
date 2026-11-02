# Implementation Plan: 第四阶段：双容器零信任物理隔离与闭包令牌 (Dual-Context Zero-Trust & Closure Tokens)

## Phase 1: 闭包安全令牌与异步防逃逸 (SecurityContext Token)
- [ ] Task: 编写 SecurityContext 闭包快照捕获与跨线程传递测试 (Red Phase)
    - [ ] 验证异步提交的任务能正确读取提交时刻的权限级别
    - [ ] 验证异步任务在定时器线程触发越权操作时被拦截
- [ ] Task: 实现 SecurityContext 及其任务包装器 (Green Phase)
    - [ ] 创建 `com.graalagent.security.SecurityContext`
    - [ ] 在 `AgentAPI.setTimeout` 和 `setInterval` 中绑定安全快照
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 2: 双容器物理隔离架构 (Dual-Context Engine)
- [ ] Task: 编写 SystemContext 与 SandboxContext 隔离性测试 (Red Phase)
    - [ ] 验证 SystemContext 具有受限 HostAccess
    - [ ] 验证 SandboxContext 无法调用任何 Java 类
- [ ] Task: 实现 ContextManager 与 SandboxContextPool (Green Phase)
    - [ ] 创建 `com.graalagent.engine.ContextManager`
    - [ ] 创建 `com.graalagent.engine.SandboxContextPool` 实现受限池化管理
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 3: 原型深冻结与沙箱 Proxy 门面 (Prototype Freezing & Proxies)
- [ ] Task: 编写原型防污染与只读 Proxy 访问测试 (Red Phase)
    - [ ] 验证修改基础原型链必定抛错
    - [ ] 验证 Proxy 屏蔽敏感方法 (如 `getClass()`)
- [ ] Task: 实现原型递归冻结与 Proxy 注入 (Green Phase)
    - [ ] 注入 ECMAScript 深度递归冻结脚本
    - [ ] 创建面向沙箱的受控 PlayerProxy / WorldProxy
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 4: 权限鉴定器与安全审计 (Permission & Audit)
- [ ] Task: 编写 PermissionEvaluator 与安全日志拦截测试 (Red Phase)
    - [ ] 验证 OP 2 级鉴权及其回退机制
    - [ ] 验证安全越权日志正常发送至 DiagnosticsHub
- [ ] Task: 实现 PermissionEvaluator (Green Phase)
    - [ ] 创建 `com.graalagent.security.PermissionEvaluator`
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
