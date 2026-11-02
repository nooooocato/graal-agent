# Implementation Plan: 第六阶段：旧版脚本兼容垫片、基准压测与交付 (Legacy Compatibility Shim & Delivery)

## Phase 1: 旧版脚本兼容垫片 (Legacy Compatibility Shim)
- [ ] Task: 编写 LegacyScriptShim 扁平脚本包装测试 (Red Phase)
    - [ ] 验证探测根目录 `*.js` 并自动生成合成 Modlet
    - [ ] 验证 `init.js` 优先执行顺序保持不变
- [ ] Task: 实现 LegacyScriptShim (Green Phase)
    - [ ] 创建 `com.graalagent.modlet.LegacyScriptShim`
    - [ ] 在 ModletManager 扫描流程末尾挂载兼容垫片
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 2: 安全渗透攻防测试套件 (Penetration Test Suite)
- [ ] Task: 编写 5 大安全漏洞全维度攻击测试用例 (Red Phase)
    - [ ] 异步定时器沙箱逃逸攻击用例
    - [ ] 宿主反射与 ClassLoader 获取攻击用例
    - [ ] 原型链污染攻击用例
    - [ ] 路径穿越逃逸攻击用例
    - [ ] `while(true)` 死循环挂起攻击用例
- [ ] Task: 运行渗透测试并进行防御合规加固 (Green Phase)
    - [ ] 运行全部渗透用例，确保全部拦截成功（绿灯）
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 3: 性能基准、压力测试与防泄露审计 (Benchmark & Stress)
- [ ] Task: 编写 100 次连续热重载与高频 Tick 压力测试 (Red Phase)
    - [ ] 监测堆内存与 Metaspace 占用
    - [ ] 验证主游戏循环 TPS 稳定性
- [ ] Task: 运行基准压测与并发优化 (Green Phase)
    - [ ] 优化沙箱池回收与垃圾回收提示
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)

## Phase 4: 生产制品打包与最终交付 (Production Delivery)
- [ ] Task: 编写打包与验证脚本
    - [ ] 配置标准打包流程与 Manifest 元数据
    - [ ] 执行生成 `graal-agent.jar` 制品
- [ ] Task: 生产集成冒烟验证
    - [ ] 携带 `-javaagent:graal-agent.jar` 在纯净 Minecraft 26+ 环境下启动运行验证
- [ ] Task: Phase Verification & Checkpoint (Refer to workflow.md)
