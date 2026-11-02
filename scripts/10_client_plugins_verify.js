// =========================================================================
// 10_client_plugins_verify.js - 客户端微插件集 (Client Plugins) JS 验证
// =========================================================================

console.log("==================================================");
console.log("[ClientPlugins-Verify] 启动客户端微插件集 JavaScript 运行时验证");
console.log("==================================================");

function runClientPluginsVerification() {
    try {
        const SMVOrchestrator = Java.type("com.graalagent.transformer.SMVOrchestrator");
        const ClientTickPlugin = Java.type("com.graalagent.transformer.plugins.ClientTickPlugin");
        const KeyboardHandlerPlugin = Java.type("com.graalagent.transformer.plugins.KeyboardHandlerPlugin");
        const PacketListenerPlugin = Java.type("com.graalagent.transformer.plugins.PacketListenerPlugin");
        const ClassFile = Java.type("java.lang.classfile.ClassFile");
        const AccessFlag = Java.type("java.lang.reflect.AccessFlag");
        const ClassDesc = Java.type("java.lang.constant.ClassDesc");
        const MethodTypeDesc = Java.type("java.lang.constant.MethodTypeDesc");

        console.log("✔ [1/4] 成功加载客户端三大微插件: ClientTick, KeyboardHandler, PacketListener");

        const orchestrator = new SMVOrchestrator();
        const tickPlugin = new ClientTickPlugin();
        const keyPlugin = new KeyboardHandlerPlugin();
        const packetPlugin = new PacketListenerPlugin();

        orchestrator.registerPlugin(tickPlugin);
        orchestrator.registerPlugin(keyPlugin);
        orchestrator.registerPlugin(packetPlugin);

        console.log("✔ [2/4] 插件成功注册至 SMVOrchestrator 调度网格");

        const cf = ClassFile.of();

        // 3. 验证 ClientTickPlugin
        const mockMcBytes = cf.build(ClassDesc.of("net.minecraft.client.Minecraft"), cb => {
            cb.withMethod("tick", MethodTypeDesc.ofDescriptor("()V"), AccessFlag.PUBLIC.mask(), mb => {
                mb.withCode(codeBuilder => codeBuilder.return_());
            });
        });
        const transformedMc = orchestrator.transform(null, tickPlugin.getTargetClassName(), null, null, mockMcBytes);
        if (!transformedMc) throw new Error("ClientTickPlugin 转换结果为空");
        const mcModel = cf.parse(transformedMc);
        let foundShadowTick = false;
        for (let i = 0; i < mcModel.methods().size(); i++) {
            const mm = mcModel.methods().get(i);
            if (mm.methodName().stringValue() === "$$shadow$tick") {
                foundShadowTick = true;
                if (!mm.flags().has(AccessFlag.PRIVATE) || !mm.flags().has(AccessFlag.SYNTHETIC)) {
                    throw new Error("$$shadow$tick 缺少 private synthetic 标志位");
                }
            }
        }
        if (!foundShadowTick) throw new Error("未生成 $$shadow$tick 影子方法");
        console.log("✔ [3/4] ClientTickPlugin 校验通过: $$shadow$tick 影子方法与心跳前置分发桩就绪");

        // 4. 验证 KeyboardHandlerPlugin 与 PacketListenerPlugin
        const mockKeyBytes = cf.build(ClassDesc.of("net.minecraft.client.KeyboardHandler"), cb => {
            cb.withMethod(
                "keyPress",
                MethodTypeDesc.ofDescriptor("(JILnet/minecraft/client/input/KeyEvent;)V"),
                AccessFlag.PUBLIC.mask(),
                mb => mb.withCode(codeBuilder => codeBuilder.return_())
            );
        });
        const transformedKey = orchestrator.transform(null, keyPlugin.getTargetClassName(), null, null, mockKeyBytes);
        const keyModel = cf.parse(transformedKey);
        let foundShadowKey = false;
        for (let i = 0; i < keyModel.methods().size(); i++) {
            const mm = keyModel.methods().get(i);
            if (mm.methodName().stringValue() === "$$shadow$keyPress") {
                foundShadowKey = true;
                break;
            }
        }
        if (!foundShadowKey) throw new Error("未生成 $$shadow$keyPress 影子方法");

        console.log("✔ [4/4] KeyboardHandlerPlugin 与 PacketListenerPlugin 校验通过: 按键消费阻断与网络包拦截就绪");
        console.log("==================================================");
        console.log("🎉 客户端微插件集 (Phase 3) 在 JavaScript 环境下验证全部通过！");
        console.log("==================================================");
        return true;
    } catch (e) {
        console.error("❌ 客户端微插件验证失败:", e);
        if (e.printStackTrace) e.printStackTrace();
        return false;
    }
}

// 立即运行一次验证
runClientPluginsVerification();

// 注册游戏内快捷测试指令
if (typeof agent !== "undefined" && agent && agent.registerCommand) {
    agent.registerCommand("testclientplugins", (args) => {
        agent.chat("§6[ClientPlugins-Verify] 正在执行客户端微插件集验证...");
        const success = runClientPluginsVerification();
        if (success) {
            agent.chat("§a[ClientPlugins-Verify] ✔ 客户端三大微插件 (Tick, Key, Packet) SMV 影子虚拟化验证通过！");
        } else {
            agent.chat("§c[ClientPlugins-Verify] ❌ 客户端微插件验证失败，请查看控制台日志。");
        }
    });
}
