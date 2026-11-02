// ==========================================
// 01_player_tracker.js - 玩家状态与世界监控
// ==========================================

let tickCount = 0;
let lastX = 0, lastY = 0, lastZ = 0;

events.onTick(() => {
    tickCount++;

    // 每 100 tick (5秒) 打印一次玩家位置（若玩家在游戏中）
    if (tickCount % 100 === 0) {
        if (player && level) {
            const x = player.getX().toFixed(1);
            const y = player.getY().toFixed(1);
            const z = player.getZ().toFixed(1);
            const health = player.getHealth().toFixed(1);
            const maxHealth = player.getMaxHealth().toFixed(1);

            // 仅在位置变化时输出日志
            if (x !== lastX || y !== lastY || z !== lastZ) {
                lastX = x; lastY = y; lastZ = z;
                console.log(`[PlayerTracker] 坐标: (${x}, ${y}, ${z}) | 生命: ${health}/${maxHealth}`);
            }
        }
    }
});
