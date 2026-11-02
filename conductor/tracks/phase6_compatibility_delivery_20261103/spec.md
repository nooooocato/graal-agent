# Specification: 第六阶段：旧版脚本兼容垫片、基准压测与交付 (Legacy Compatibility Shim & Delivery)

## Overview
本 Track 落实新一代架构技术蓝图第六阶段（参见 [docs/new_base/Refactoring-and-Migration-Plan.md](../../../docs/new_base/Refactoring-and-Migration-Plan.md)），旨在确保平滑过渡，为旧有脚本提供向后兼容性，并通过严密的**安全渗透攻防测试**、**死循环防卡死压力测试**、**20 TPS 长期稳定性压测**，最终打包交付生产级 JAR 构建。

详细设计规范参照：
- [Refactoring-and-Migration-Plan.md](../../../docs/new_base/Refactoring-and-Migration-Plan.md)
- [Vision-and-Principles.md](../../../docs/new_base/Vision-and-Principles.md)

---

## Functional Requirements

### 1. 旧版扁平脚本兼容垫片 (`LegacyScriptShim`)
- 自动探测 `scripts/` 根目录下的历史扁平 `*.js` 文件（非 `modlets/` 子目录下的旧代码）；
- 自动将其包装为名为 `legacy_scripts` 的虚拟合成 Modlet，生成虚拟 `manifest.json`；
- 继承原先的 `init.js` 与 `00_` 优先执行规则；
- 确保现有用户的历史脚本在未经任何修改的前提下依然 100% 正常运行。

### 2. 全方位安全渗透攻防测试套件 (Penetration Test Suite)
构建端到端的安全攻防验证用例，覆盖所有前代已知与潜在漏洞：
- **定时器逃逸验证**：验证在受限沙箱中调用 `setTimeout` 或 `setInterval` 写入磁盘必被拦截；
- **反射逃逸验证**：验证在沙箱中调用 `Java.type`、`Java.extend` 或访问 `player.getClass()` 必被拦截；
- **原型链污染验证**：验证向 `Object.prototype` 注入属性必被深冻结机制抛错拦截；
- **路径穿越验证**：验证通过 `../../` 试图读取敏感文件必被拦截；
- **死循环自愈验证**：验证玩家输入 `while(true) {}` 必在 50ms 内被看门狗硬中断，游戏主线程 TPS 保持 20.0 不卡顿。

### 3. 系统并发与内存泄露基准测试 (Benchmark Suite)
- **热重载压力测试**：执行连续 100 次脚本热重载，监测 JVM 堆内存与 Metaspace，验证 Context 成功闭包回收，无 ClassLoader 内存泄露；
- **高频事件吞吐测试**：在每刻派发 1000 次 tick 事件，验证主循环延迟增量 $\le 0.5\text{ ms}$。

### 4. 生产级发布与制品构建
- 配置生产级打包脚本，生成干净的 `graal-agent.jar`；
- 编写最终发布的部署与配置文档。

---

## Non-Functional Requirements
- **稳定性**：在连续 24 小时压测中崩溃率为 **0**；
- **TPS 损耗**：在无重型脚本负载下，开启 Agent 造成的游戏 TPS 损耗 $\le 0.1$。

---

## Acceptance Criteria
- [ ] 放置一段旧版 `test.js` 在 `scripts/` 下，验证 `LegacyScriptShim` 能够无感知成功执行并响应游戏事件；
- [ ] 安全渗透测试套件中所有 5 种攻击手段全部被成功防御，测试用例全部绿灯；
- [ ] 连续 100 次热重载测试通过，无 `OutOfMemoryError`；
- [ ] 成功构建出可直接挂载至 Minecraft 26+ 生产启动的 `graal-agent.jar`。
