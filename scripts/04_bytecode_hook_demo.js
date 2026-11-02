// ==========================================
// 04_bytecode_hook_demo.js - 字节码转换与监听演示
// ==========================================

// 监听 Minecraft 类加载过程中的字节码
agent.registerTransformer((className, bytes) => {
    // 监听特定类加载
    if (className === "net/minecraft/client/gui/screens/TitleScreen") {
        console.log(`[Bytecode] 检测到主菜单类加载: ${className} (字节大小: ${bytes.length})`);
    }

    // 若需要替换字节码，可返回修改后的 byte[]；若不修改返回 null 即可
    return null;
});
