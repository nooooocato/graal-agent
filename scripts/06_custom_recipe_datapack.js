// ==========================================
// 06_custom_recipe_datapack.js - 动态生成数据包配方示例 (纯服务端即插即用)
// ==========================================

function ensureSampleRecipeDatapack() {
    try {
        // 1. 初始化数据包目录与 pack.mcmeta
        agent.createDatapack("js_recipes", "MC-JS-Agent 动态合成配方数据包");

        // 2. 写入自定义合成表: 2个泥土合成1颗钻石 (测试原版客户端直连生效)
        const recipeJson = JSON.stringify({
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [
                "minecraft:dirt",
                "minecraft:dirt"
            ],
            "result": {
                "count": 1,
                "id": "minecraft:diamond"
            }
        }, null, 2);

        fs.writeFile("datapacks/js_recipes/data/mcagent/recipe/dirt_to_diamond.json", recipeJson);
        console.log("[Datapack] 成功生成动态数据包配方: 2x 泥土 => 1x 钻石");
    } catch (err) {
        console.error("[Datapack] 生成配方失败: " + err.message);
    }
}

// 自动生成示例数据包配方
ensureSampleRecipeDatapack();

// 提供手动刷新与生成指令
agent.registerCommand("synthrecipe", (args) => {
    ensureSampleRecipeDatapack();
    agent.chat("§a[Datapack] 示例配方 (2x 泥土 => 1x 钻石) 已写入 scripts/datapacks/js_recipes!");
    agent.chat("§e执行 /reload 或 /reloadjs 即可立即生效！");
});
