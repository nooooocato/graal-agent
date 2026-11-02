// ==========================================
// 00_init.js - 基础环境配置与启动问候
// ==========================================

console.log("==========================================");
console.log("  Minecraft 26.3 JS Script Engine Active  ");
console.log("==========================================");

// 监听游戏初始化事件
events.onInit((minecraft) => {
    console.log("[Init] Minecraft 实例已就绪!");
    const version = (minecraft && minecraft.getLaunchedVersion) 
        ? minecraft.getLaunchedVersion() 
        : ((minecraft && minecraft.getServerVersion) ? minecraft.getServerVersion() : "26.3");
    console.log("[Init] 启动版本: " + version);
    
    // 定时检查玩家登录状态并发送欢迎消息
    const checkTimer = setInterval(() => {
        if (player) {
            agent.chat("§a[Graal-JS-Agent] 脚本引擎已连接! 可在聊天栏输入 §e/js <代码> §a求值，输入 §e/reloadjs §a重载。");
            clearInterval(checkTimer);
        }
    }, 1000);
});
