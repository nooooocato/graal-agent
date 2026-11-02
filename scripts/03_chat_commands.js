// ==========================================
// 03_chat_commands.js - 接入标准 MC 指令管道与自定义指令
// ==========================================

// 1. 注册标准 MC 客户端指令 (以 / 开头，支持原版自动补全与语法高亮)
agent.registerCommand("mypos", (args) => {
    if (player) {
        const x = player.getX().toFixed(2);
        const y = player.getY().toFixed(2);
        const z = player.getZ().toFixed(2);
        const yaw = player.getYRot().toFixed(1);
        const pitch = player.getXRot().toFixed(1);
        agent.chat(`§b[我的坐标] §fX: §e${x} §fY: §e${y} §fZ: §e${z} §7(Yaw: ${yaw}, Pitch: ${pitch})`);
    }
});

agent.registerCommand("myfly", (args) => {
    if (player) {
        const abilities = player.getAbilities();
        abilities.mayfly = !abilities.mayfly;
        agent.chat(`§a[飞行权限] §f已切换为: §e${abilities.mayfly ? "开启" : "关闭"}`);
    }
});

agent.registerCommand("jshelp", (args) => {
    agent.chat("§6====== [MC-JS-Agent 标准指令列表] ======");
    agent.chat("§e/js <表达式> §7- 在客户端执行 JS 脚本表达式 (如: /js player.getHealth())");
    agent.chat("§e/reloadjs §7- 热重载全部 JS 脚本");
    agent.chat("§e/mypos §7- 获取高精度坐标与朝向");
    agent.chat("§e/myfly §7- 切换客户端飞行能力");
});

// 2. 传统聊天消息前缀 (#help, #pos) 兼容监听
events.onChat((message) => {
    if (!message || !message.startsWith("#")) {
        return false;
    }

    const cmd = message.trim().split(" ")[0].toLowerCase();
    if (cmd === "#help") {
        agent.chat("§a提示: 推荐直接使用原版指令管道，输入 §e/jshelp §a查看帮助！");
        return true;
    }
    return false;
});
