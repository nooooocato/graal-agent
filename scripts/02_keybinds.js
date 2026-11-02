// ==========================================
// 02_keybinds.js - 快捷键事件监听示例
// ==========================================

// GLFW 按键码参考:
// 74 = 'J'
// 75 = 'K'
// 71 = 'G'
// 82 = 'R'

events.onKey((window, key, event) => {
    // 检查玩家是否已经在世界中且未打开输入界面
    if (!player || (mc && mc.screen !== null)) {
        return false;
    }

    // 按下 'J' 键 (GLFW 74)
    if (key === 74) {
        agent.chat(`§6[快捷键 J] §f当前玩家生命值: §a${player.getHealth().toFixed(1)} §f/ §c${player.getMaxHealth().toFixed(1)}`);
        return true; // 返回 true 阻止原版默认行为
    }

    // 按下 'K' 键 (GLFW 75) - 显示当前方块与世界信息
    if (key === 75) {
        const x = Math.floor(player.getX());
        const y = Math.floor(player.getY());
        const z = Math.floor(player.getZ());
        const biome = level.getBiome(player.blockPosition()).unwrapKey().get().location().toString();
        
        agent.chat(`§d[快捷键 K] §f当前位置: §e[${x}, ${y}, ${z}] §f群系: §b${biome}`);
        return true;
    }

    return false;
});
