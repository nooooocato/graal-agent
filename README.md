# Minecraft 26.3 JavaScript Agent (graal-agent)

为 Minecraft 26.3（无混淆 / Mojang 映射版本）打造的现代化 Java Agent。基于 **Oracle GraalVM JDK 25** 的 **Polyglot JavaScript (GraalJS)** 引擎与 Java 25 原生 **Class-File API**，实现原生 JS 脚本交互、事件监听、热重载与标准原版指令管道集成。

---

## 🌟 核心特性

1. **接入标准 Minecraft 原版指令管道**
   - 彻底摆脱聊天前缀，全面接入 Minecraft 原版 `/` 指令系统与 Brigadier 语法树。
   - 输入 **`/js <代码>`**：客户端原生指令执行，回显执行结果。
   - 输入 **`/reloadjs`**：热重载全部 JS 脚本。
   - 支持通过 `agent.registerCommand("mycmd", callback)` 在 JS 中向原版注册自定义 `/mycmd` 指令！
2. **渲染线程安全 (Thread-Safe Dispatching)**
   - 彻底修复 `Rendersystem called from wrong thread` 报错。所有聊天发送与 GUI 操作自动通过 `mc.execute(...)` 调度至 Minecraft Render 主线程，无论是定时器还是后台线程调用均绝对安全。
3. **全链路 UTF-8 字符集支持**
   - 彻底解决 Windows 平台的中文乱码问题。脚本读取、本地日志落盘（`agent.log`）及控制台/Log4j 统一采用标准 UTF-8。
4. **GraalVM Truffle JIT 极致性能**
   - 依赖 GraalVM 25.0.4 的 Polyglot Truffle 编译器，支持 ES2024+ 现代 JavaScript 规范（类、解构、Promise、Async/Await、箭头函数等）。
5. **Java 25 原生字节码插桩 (ClassFile API)**
   - 使用 Java 25 标准库 `java.lang.classfile`，天然兼容 Class Version 69，零三方字节码依赖。
6. **自动依赖自举 & 实时热重载 (Hot-Reload)**
   - 动态挂载 `lib/` 依赖。通过 `WatchService` 实时监听 `scripts/`，修改保存即刻生效。

---

## 📁 目录结构

```text
H:\mc-mod\graal-agent\
├── lib\                       # GraalJS 25.0.4 运行时依赖（自动注入 Classpath）
├── scripts\                   # JavaScript 脚本目录（支持热重载）
│   ├── 00_init.js             # 启动问候与初始化
│   ├── 01_player_tracker.js   # 玩家坐标与生命监听 (events.onTick)
│   ├── 02_keybinds.js         # 快捷键监听 (events.onKey)
│   ├── 03_chat_commands.js    # 标准原版指令与自定义指令演示
│   └── 04_bytecode_hook_demo.js # 动态字节码拦截演示
├── src\                       # Agent Java 源码
│   └── com\graalagent\
│       ├── AgentMain.java             # Agent 入口 (premain / agentmain)
│       ├── bridge\AgentBridge.java    # 字节码回调、主线程调度与反射桥接
│       ├── transformer\MinecraftTransformer.java # Java 25 ClassFile 插桩器
│       ├── script\ScriptManager.java  # 指令分发、上下文管理与热重载
│       ├── script\EventBus.java       # 事件总线
│       ├── script\AgentAPI.java       # 注入 JS 的 agent 全局 API
│       └── util\Logger.java          # UTF-8 格式化文件与 Log4j 日志
├── build.ps1                  # PowerShell 7 一键编译构建脚本
├── graal-agent.jar               # 已编译打包的 Java Agent
└── README.md                  # 本文档
```

---

## 🎮 标准原版指令使用指南

在游戏中直接按下 **`/`** 键呼出指令栏：

### 1. 执行 JavaScript 脚本表达式
```text
/js player.getX()
/js player.getHealth()
/js player.sendSystemMessage(Java.type('net.minecraft.network.chat.Component').literal("Hello from command!"))
/js mc.options.gamma().set(100.0)
```

### 2. 热重载所有脚本
```text
/reloadjs
```

### 3. 自定义指令注册 (在 JS 中编写)
在任意 `.js` 脚本中调用 `agent.registerCommand`：
```javascript
// 注册 /mypos 指令
agent.registerCommand("mypos", (args) => {
    if (player) {
        agent.chat("§b当前坐标: " + player.getX().toFixed(2) + ", " + player.getY().toFixed(2) + ", " + player.getZ().toFixed(2));
    }
});

// 注册 /myfly 指令
agent.registerCommand("myfly", (args) => {
    if (player) {
        const ab = player.getAbilities();
        ab.mayfly = !ab.mayfly;
        agent.chat("飞行已切换为: " + ab.mayfly);
    }
});
```
输入 `/mypos` 或 `/myfly` 即可直接执行！

---

## 🛠 重新编译与应用更新

由于 Windows 系统会锁定正在运行中游戏所加载的 `.jar` 文件：
1. **保存并退出当前 Minecraft**。
2. 在 PowerShell 7 中运行一键编译：
   ```powershell
   cd H:\mc-mod\graal-agent
   .\build.ps1
   ```
3. 重新在 HMCL 中启动游戏即可享用最新版！
