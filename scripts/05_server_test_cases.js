// ==========================================
// 05_server_test_cases.js - 验证服务端指令与双端交互测试用例
// ==========================================

// 1. 注册环境信息测试指令 /serverinfo
agent.registerCommand('serverinfo', (args) => {
    const isClient = agent.isClient();
    const isDedicated = agent.isDedicatedServer();
    agent.chat('§6====== [Graal-JS-Agent 运行环境信息] ======');
    agent.chat(`§b环境类型: §e${isDedicated ? 'Dedicated Server (专用服务端)' : (isClient ? 'Client (客户端/内置服务端)' : '未知')}`);
    if (server) {
        agent.chat(`§b服务端版本: §e${server.getServerVersion()}`);
        agent.chat(`§b在线玩家数: §e${server.getPlayerCount()}`);
    } else {
        agent.chat('§7服务端实例尚未初始化');
    }
});

// 2. 注册动态计算测试指令 /evaltest
agent.registerCommand('evaltest', (args) => {
    const expr = args && args.trim().length > 0 ? args.trim() : '2 + 3 * 4';
    const res = agent.eval(expr);
    agent.chat(`§a[表达式求值] §f${expr} §e=> ${res}`);
});

// 3. 注册文件系统测试指令 /fstest
agent.registerCommand('fstest', (args) => {
    try {
        fs.writeFile('test_verify.txt', 'Graal-JS-Agent File I/O Verified! 时间戳: ' + Date.now());
        const content = fs.readFile('test_verify.txt');
        agent.chat('§a[FS测试成功] 写入并读取内容: §f' + content);
    } catch (err) {
        agent.chat('§c[FS测试失败] ' + err.message);
    }
});
