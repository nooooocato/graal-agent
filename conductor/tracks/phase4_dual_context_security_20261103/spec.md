# Specification: 第四阶段：双容器零信任物理隔离与闭包令牌 (Dual-Context Zero-Trust & Closure Tokens)

## Overview
本 Track 落实新一代架构技术蓝图第四阶段（参见 [docs/new_base/Refactoring-and-Migration-Plan.md](../../../docs/new_base/Refactoring-and-Migration-Plan.md)），旨在消除单容器权限污染隐患，并从数学与物理层面上彻底封死**“`setTimeout` 异步回调脱离沙箱标记逃逸”**这一致命漏洞。

详细设计规范参照：
- [Module-Security-Specification.md](../../../docs/new_base/Module-Security-Specification.md)
- [Module-DualContext-Engine.md](../../../docs/new_base/Module-DualContext-Engine.md)

---

## Functional Requirements

### 1. 不可变闭包安全令牌 (`SecurityContext`)
- 放弃前代单纯依赖脆弱的局部 `ThreadLocal<Boolean>`；
- 创建不可变的 `SecurityContext` 身份记录对象，包含调用者 UUID、控制台标记与权限级别；
- 异步任务（如 `agent.setTimeout`、`setInterval`、Promise）在提交瞬间必须自动捕获并快照绑定当前的 `SecurityContext`；
- 在任何线程执行回调前强制执行上下文复原，`finally` 块中清理，彻底阻断任何跨线程权限逃逸。

### 2. 双容器物理隔离体系 (`ContextManager` & `SandboxContextPool`)
- **特权系统容器 (`SystemContext`)**：单例运行，供常驻系统脚本、Modlet 初始化与控制台求值。配置受控的 `HostAccess.EXPLICIT`；
- **零信任沙箱容器池 (`SandboxContextPool`)**：用于游戏内玩家指令，配置 `HostAccess.NONE`。彻底切除一切 Java 反射，禁止调用 `Java.type`，仅通过受控的 Proxy DTO 与游戏状态安全交互。

### 3. 原型链深度递归冻结 (Prototype Freezing)
- 在沙箱容器就绪后，执行预置脚本深度递归冻结 `Object.prototype`、`Array.prototype`、`Function.prototype` 等关键原型，严防原型污染漏洞。

### 4. 权限鉴定与安全遥测 (`PermissionEvaluator`)
- 基于 Minecraft 26+ 最新 `Commands.LEVEL_ADMINS` 与 `PermissionSet` 进行 OP 2 级鉴权；
- 拦截任何越权行为，并向安全审计通道发送高维度的 JSON 结构化安全日志（包含时间、玩家信息、越权代码片段）。

---

## Non-Functional Requirements
- **安全性**：沙箱逃逸渗透测试成功率为 **0**；
- **开销**：`SecurityContext` 闭包包装仅增加纳秒级局部引用赋值开销。

---

## Acceptance Criteria
- [ ] 渗透测试：在受限沙箱中通过 `setTimeout` 试图异步读取/写入宿主文件系统，验证必被抛出 `SecurityException` 拦截；
- [ ] 渗透测试：在沙箱中试图执行 `Java.type("java.lang.System")` 或利用 `player.getClass()` 探测反射，验证必被抛出异常拦截；
- [ ] 渗透测试：尝试修改 `Array.prototype.push`，验证因原型深冻结而无法被篡改。
