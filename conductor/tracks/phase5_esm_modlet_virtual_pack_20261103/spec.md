# Specification: 第五阶段：ESM Modlet 模块生态与虚拟内存数据包 (ESM Modlet System & Virtual In-Memory Datapack)

## Overview
本 Track 落实新一代架构技术蓝图第五阶段（参见 [docs/new_base/Refactoring-and-Migration-Plan.md](../../../docs/new_base/Refactoring-and-Migration-Plan.md)），旨在将脚本开发体验从简陋的单文件平铺加载全面升级为**现代 Web 标准 ECMAScript 模块 (ESM)** 与 **Modlet 独立包管理**，并将数据包资源生成演进为**纯内存虚拟流 (Virtual In-Memory Datapack)**，彻底消灭磁盘 I/O 碎片与重载延迟。

详细设计规范参照：
- [Module-ESM-Modlet-System.md](../../../docs/new_base/Module-ESM-Modlet-System.md)
- [Module-Virtual-Datapack.md](../../../docs/new_base/Module-Virtual-Datapack.md)

---

## Functional Requirements

### 1. 标准 ESM 运行时与路径解析器
- 启用 GraalVM 原生 ESM 模块支持（MIME: `application/javascript+module`），支持标准 `import { ... } from './utils.js'` 跨文件引用；
- 实现定制的本地相对路径解析策略，确保严格在 `scripts/` 工作目录边界内定位模块文件，防止目录遍历逃逸。

### 2. Modlet 模块包体系与生命周期 (`ModletManager`)
- 规范化独立模块包目录结构：`scripts/modlets/<modlet-id>/`（含 `manifest.json` 与 `index.js`）；
- 解析 `manifest.json` 元数据，构建依赖关系有向无环图（DAG）并进行拓扑排序；
- 声明式生命周期钩子：
  - `export function onEnable(ctx) {}`：模块挂载时自动注入专有隔离上下文门面；
  - `export function onDisable() {}`：模块卸载或重载时触发清理；
- **单包增量热重载**：保存单个 Modlet 源码时，仅重载该 Modlet 及其下游受影响包，其他 Modlet 零中断常驻运行。

### 3. 虚拟内存数据包引擎 (`VirtualPackResources`)
- 直接实现 Minecraft 原版的 `PackResources` 接口；
- 自定义配方（Recipes）、标签（Tags）、进度（Advancements）与战利品表（Loot Tables）纯粹保存在并发内存字典中（`ConcurrentHashMap<ResourceLocation, byte[]>`），完全不向磁盘写入物理文件；
- 挂载 `VirtualRepositorySource` 至原版 `PackRepository`；
- 兼容物理回退：自动扫描并加载 `scripts/datapacks/` 中的物理 ZIP 或目录包。

### 4. 声明式 JavaScript 数据包 DSL
- 提供直观的 JS 注册 API：
  - `agent.datapack.addRecipe(id, recipeJson)`
  - `agent.datapack.addTag(type, tagId, entries)`
  - `agent.datapack.addLootTable(id, tableJson)`
  - `agent.datapack.reload()`：一键触发原版服务端动态资源重载。

---

## Non-Functional Requirements
- **I/O 性能**：内存虚拟数据包热重载耗时 $\le 20\text{ ms}$，物理磁盘写入次数为 **0**；
- **规范标准**：符合 ECMAScript 2024 (ES15) 国际标准规范。

---

## Acceptance Criteria
- [ ] 针对 ESM 模块解析的测试通过，验证两个具有互相引用关系的 `.js` 文件成功通过 `import/export` 编译执行；
- [ ] 针对 Modlet 依赖拓扑排序与 `onEnable`/`onDisable` 生命周期的测试通过；
- [ ] 针对 `VirtualPackResources` 的测试通过，验证能从内存流中正确读取 `pack.mcmeta` 及动态添加的配方 JSON 数据；
- [ ] 在 JS 中调用 `agent.datapack.addRecipe` 后触发 reload，验证数据包管线无异常成功刷新。
