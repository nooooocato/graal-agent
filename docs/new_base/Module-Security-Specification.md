---
title: 模块规范：闭包安全令牌与零信任沙箱规范 (Security Specification)
date: 2026-11-03
tags:
  - module
  - security
  - zero-trust
  - capability-token
  - anti-escape
  - audit
  - new-base
---

# 模块规范：闭包安全令牌与零信任沙箱规范 (Security Specification)

本模块规范基于对前代沙箱机制（参见 [[Module-Security|旧版安全模块分析]]）中致命的“异步定时器沙箱逃逸”与“`HostAccess.ALL` 反射越权隐患”的深入剖析，确立一套**基于闭包不可变安全令牌（SecurityContext Token）**与**零信任显式白名单（Zero-Trust HostAccess）**的工业级安全规范。

---

## 终结“异步定时器逃逸”的数学级防线

### 1. 致命漏洞机理复盘
在前代实现中，安全判断依托于当前工作线程的 `ThreadLocal<Boolean>`：
- 当玩家在游戏内输入 `/js setTimeout(() => { ... }, 10)` 时；
- 主命令线程在进入 `setTimeout` 注册时是沙箱态，但在命令返回后立即执行了 `clear()`；
- 10 毫秒后，定时任务在独立的 `graal-agent-timer` 异步线程中被唤醒。**由于该异步线程从未被设为沙箱态，其 `ThreadLocal` 值为默认的 `false`！**
- 结果：定时器回调内部获得了与控制台等同的最高管理员权限，玩家可以利用该回调肆意读写服务器任何私密文件、甚至反弹 Shell！

### 2. 解决方案：不可变闭包安全令牌 (`SecurityContext`)
新架构规范确立：**禁止仅依赖隐式线程局部变量判断权限，所有异步任务提交时必须对发起者的安全身份实施物理快照绑定！**

```mermaid
sequenceDiagram
    autonumber
    actor Player as 受限玩家
    participant Cmd as 指令解析器
    participant Agent as AgentAPI.setTimeout
    participant Pool as 异步定时器线程池
    participant Exec as 回调执行上下文

    Player->>Cmd: 发起带定时器的受限代码
    Cmd->>Cmd: 捕获当前线程快照: SecurityContext(PlayerUUID, Level=SANDBOX)
    Cmd->>Agent: setTimeout(callback, 100)
    Agent->>Agent: 闭包封印: 将 SecurityContext 固化绑定入 ScheduledTask
    Agent->>Pool: 提交调度任务

    Note over Pool,Exec: 100ms 之后，异步线程被唤醒
    Pool->>Exec: 准备触发回调函数
    Exec->>Exec: 强行还原绑定的快照: SecurityContext.apply(snapshot)
    
    opt 尝试越权调用
        Exec->>Exec: 试图访问 agent.fs.writeFile(...)
        Exec-->>Player: 抛出 SecurityException! 权限拒绝，沙箱依旧牢不可破!
    end
    Exec->>Exec: finally: SecurityContext.clear()
```

#### 规范实现代码设计：
```java
public record SecurityContext(
        UUID subjectUUID,
        boolean isConsole,
        PermissionLevel permissionLevel,
        long creationTimestamp
) {
    private static final ThreadLocal<SecurityContext> CURRENT = ThreadLocal.withInitial(SecurityContext::anonymous);

    public static SecurityContext capture() {
        return CURRENT.get();
    }

    public Runnable wrap(Runnable task) {
        final SecurityContext snapshot = this;
        return () -> {
            SecurityContext previous = CURRENT.get();
            CURRENT.set(snapshot);
            try {
                task.run();
            } finally {
                CURRENT.set(previous);
            }
        };
    }
}
```

通过这一闭包包装机制，无论异步任务被延迟多久、在哪个线程池（虚拟线程、定时器线程、网络回调线程）执行，它都**永远携带着创建时刻的沙箱镣铐**，从底层逻辑上消除了线程切换造成的逃逸死角！

---

## 零信任宿主访问规范 (Zero-Trust HostAccess)

前代代码采用了危险的 `HostAccess.ALL`，意味着脚本一旦拿到任何 Java 对象的指针，理论上均可调用其所有公开方法（包括 `getClass()`、`wait()` 等）。

新架构规范全面推行 **分级零信任访问策略**：

### 1. 玩家沙箱：`HostAccess.NONE` + 受控 DTO 代理
针对游戏内玩家输入的脚本：
- **完全关闭 Java 原生对象直接暴露**：配置 `HostAccess.NONE`，禁止任何未经包装的 Java 原生对象落入 JS 环境；
- **只暴露受控 Proxy**：仅向沙箱注入受控的领域对象代理（如 `PlayerProxy`, `WorldProxy`），代理内部仅暴露经过严格安全审计的业务方法（如 `sendMessage`, `teleport`, `getBlock`），从根本上切断一切基于 Java 反射的探测（如 `Java.type`、`obj.getClass().forName(...)`）。

### 2. 系统环境：`HostAccess.EXPLICIT`
针对特权系统上下文（System Context）：
- 仅允许访问显式标注了 `@HostAccess.Export` 的 API 方法；
- 严禁向脚本暴露任何能够获取 `ClassLoader`、`Field` 或动态生成类的高危句柄。

---

## 原型链深冻结规范 (Prototype Tampering Defense)

在 ECMAScript 中，攻击者常利用“原型链污染”（Prototype Pollution）修改基础对象：
```javascript
// 恶意破坏全局数组行为
Array.prototype.push = function() { throw new Error("hacked"); };
```
如果所有上下文共享同一个原型链，其他正常模块将全面瘫痪。

新架构规范规定：**在每个沙箱容器初始化就绪后，必须立即执行原生原型链深度递归冻结！**
```javascript
(function freezeAll() {
    const targets = [Object, Array, Function, String, Number, Boolean, Promise, Map, Set];
    for (const target of targets) {
        if (target && target.prototype) {
            Object.freeze(target.prototype);
            Object.freeze(target);
        }
    }
})();
```
彻底阻断任何对宿主全局环境的基础原型篡改。

---

## 统一安全审计日志 (Security Telemetry)

任何触发权限违规的操作，系统必须记录包含高维度上下文的结构化安全日志：
```json
{
  "event": "SECURITY_VIOLATION",
  "timestamp": "2026-11-03T00:22:00Z",
  "subject": "Player_Steve (c49e7b23-....)",
  "permission_level": "PLAYER_SANDBOX",
  "action": "ACCESS_DENIED_FILE_SYSTEM",
  "target_path": "../../server.properties",
  "code_snippet": "agent.fs.readFile('../../server.properties')",
  "stack_trace": "SecurityException: 安全权限拒绝 at ScriptFileSystem.resolvePath..."
}
```
此类日志将由 [[Module-Diagnostics-and-Logging|诊断子系统]] 实时推送到专属的安全审计管道，供管理员事后追踪溯源。

---

## 相关文档双链

- [[Index]] - 返回技术蓝图索引
- [[System-Architecture]] - 查看系统分层拓扑
- [[Module-DualContext-Engine]] - 深入双容器与看门狗机制
- [[Module-Bridge-and-Dispatcher]] - 深入权限鉴定与网关
- [[Module-Diagnostics-and-Logging]] - 深入审计日志
