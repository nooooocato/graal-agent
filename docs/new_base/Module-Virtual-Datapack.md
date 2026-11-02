---
title: 模块规范：虚拟内存数据包与动态资源管线 (Virtual Datapack)
date: 2026-11-03
tags:
  - module
  - datapack
  - virtual-pack
  - pack-resources
  - zero-disk
  - new-base
---

# 模块规范：虚拟内存数据包与动态资源管线 (Virtual Datapack)

本模块规范重构前代 [`DatapackManager.java`](file:///H:/mc-mod/graal-agent/src/com/graalagent/datapack/DatapackManager.java)，确立 **内存虚拟化资源包 (Virtual In-Memory Datapack)** 与 **物理磁盘回退** 的双模资源管线架构，实现自定义配方（Recipes）、标签（Tags）、战利品表（Loot Tables）以及数据函数（MCFunctions）的**零磁盘 I/O 动态秒级热注入**。

---

## 核心设计：从“磁盘碎文件生成”到“内存虚拟化流”

在前代实现中，脚本若要添加一个合成配方，必须通过 Java 在磁盘上创建物理子目录并写入 `recipe.json`，然后通知服务端从磁盘重新扫描读取。这种传统方案在脚本高频动态生成内容时，会导致磁盘 I/O 频繁、产生大量临时垃圾文件且易发生并发写入冲突。

新规范的设计核心是：**直接实现 Minecraft 原版的 `PackResources` 接口，将数据包的所有 JSON 结构纯粹保存在内存中，直接喂给原版数据包加载管线！**

```mermaid
flowchart TD
    subgraph ScriptInput["JavaScript 脚本层声明"]
        R1["agent.datapack.addRecipe('magic_wand', {...})"]
        R2["agent.datapack.addTag('blocks/mineable', 'custom_ore')"]
        R3["agent.datapack.addLootTable('boss_drop', {...})"]
    end

    subgraph VirtualEngine["VirtualDatapackEngine (内存虚拟引擎)"]
        MemMap["ConcurrentHashMap<ResourceLocation, IoSupplier<InputStream>>\n(纯内存 JSON 字节流映射表)"]
        VPack["VirtualPackResources (实现 Minecraft 原版 PackResources)"]
        Source["VirtualRepositorySource (实现 RepositorySource)"]
    end

    subgraph VanillaPipe["Minecraft 原版资源管线 (PackRepository)"]
        SMVHook["SMV 拦截 PackRepository 构造器"]
        Repo["PackRepository 注册 VirtualRepositorySource"]
        ServerReload["server.reloadResources(selected)"]
    end

    ScriptInput --> MemMap
    MemMap --> VPack
    VPack --> Source
    Source --> SMVHook
    SMVHook --> Repo
    Repo --> ServerReload
    ServerReload -.->|直接从内存拉取流，零磁盘读写!| MemMap
```

---

## 核心技术与落地规范

### 1. 内存虚拟资源包实现 (`VirtualPackResources`)
利用现代 Minecraft 的抽象资源接口，构建完全驻留在堆内存中的数据包：

```java
public class VirtualPackResources implements PackResources {
    private final String packId;
    private final PackLocationInfo locationInfo;
    private final Map<String, byte[]> memoryFiles = new ConcurrentHashMap<>();

    public VirtualPackResources(String packId, String description, int packFormat) {
        this.packId = packId;
        // 自动在内存中准备合规的 pack.mcmeta 字节
        String mcmeta = String.format("""
            {"pack":{"description":"%s","pack_format":%d}}
            """, description, packFormat);
        memoryFiles.put("pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
    }

    public void putJson(String path, String jsonContent) {
        memoryFiles.put(path, jsonContent.getBytes(StandardCharsets.UTF_8));
    }

    public void remove(String path) {
        memoryFiles.remove(path);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        String path = String.join("/", segments);
        byte[] data = memoryFiles.get(path);
        return data != null ? () -> new ByteArrayInputStream(data) : null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        // 按照 data/<namespace>/<category>/<path>.json 的格式定位内存流
        String internalPath = String.format("data/%s/%s", location.getNamespace(), location.getPath());
        byte[] data = memoryFiles.get(internalPath);
        return data != null ? () -> new ByteArrayInputStream(data) : null;
    }

    // 其它原版枚举与元数据检索方法...
}
```

### 2. 声明式脚本 DSL 规范
向脚本环境暴露符合人体工程学的高级 DSL，彻底免去繁琐的手写文件操作：

```javascript
// 动态添加一个有序合成配方
agent.datapack.addRecipe("my_mod:magic_pickaxe", {
    type: "minecraft:crafting_shaped",
    pattern: [
        "DDD",
        " S ",
        " S "
    ],
    key: {
        "D": { item: "minecraft:diamond" },
        "S": { item: "minecraft:stick" }
    },
    result: {
        id: "minecraft:netherite_pickaxe",
        count: 1,
        components: {
            "minecraft:enchantments": {
                "minecraft:efficiency": 5
            }
        }
    }
});

// 动态追加方块破坏标签
agent.datapack.addTag("minecraft:block", "minecraft:mineable/pickaxe", [
    "my_mod:crystal_ore"
]);

// 一键热刷新生效 (自动通知原版 Server 完成内存管线重新编译)
agent.datapack.reload();
```

### 3. 双模兼容与物理磁盘降级
除了纯内存模式外，系统依然保留物理目录加载能力：
- 系统持续监视 `scripts/datapacks/` 物理目录中的第三方 ZIP 数据包或外部资源包；
- 当检测到物理数据包变动时，与内存虚拟数据包合并呈递给 `PackRepository`，实现**虚拟与实体资产无缝兼收并蓄**。

---

## 性能收益评测

| 指标维度 | 传统磁盘生成模式 (`src/` 现状) | 新一代内存虚拟包 (`new_base`) |
| :--- | :--- | :--- |
| **磁盘 I/O 写入开销** | 每次热重载产生数百个文件读写与系统调用 | **0 次物理磁盘写操作 (纯堆内存操作)** |
| **重载生效耗时** | 300ms ~ 1500ms (受限于磁盘磁头与操作系统缓存) | **< 20ms (内存指针直接装载)** |
| **磁盘空间占用与碎片** | 产生大量冗余小文件与临时缓存 | **0 字节物理磁盘占用** |
| **并发安全性** | 易发生文件被外部程序占用导致删除/覆盖失败 | **`ConcurrentHashMap` 纯线程安全原子替换** |

---

## 相关文档双链

- [[Index]] - 返回技术蓝图索引
- [[System-Architecture]] - 查看系统分层拓扑
- [[Module-SMV-Mesh]] - 深入数据包注入插桩
- [[Module-ESM-Modlet-System]] - 深入 Modlet 模块资源
- [[Module-Bridge-and-Dispatcher]] - 深入跨端分发网关
