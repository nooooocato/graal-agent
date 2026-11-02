// =========================================================================
// 09_smv_orchestrator_verify.js - SMVOrchestrator 调度器与插件流水线 JS 验证
// =========================================================================

console.log("==================================================");
console.log("[Orchestrator-Verify] 启动 SMVOrchestrator 插件流水线 JavaScript 验证");
console.log("==================================================");

function runOrchestratorVerification() {
    try {
        const SMVOrchestrator = Java.type("com.graalagent.transformer.SMVOrchestrator");
        const ClassTransformPlugin = Java.type("com.graalagent.transformer.ClassTransformPlugin");
        const ClassFile = Java.type("java.lang.classfile.ClassFile");

        console.log("✔ [1/4] 成功加载 SMVOrchestrator 与 ClassTransformPlugin 接口");

        const orchestrator = new SMVOrchestrator();
        let plugin1Executed = false;
        let plugin2Executed = false;
        const targetClass = "game/entity/CustomZombie";

        // 1. 使用 Java.extend 在 JavaScript 中实现第一个微插件
        const Plugin1 = Java.extend(ClassTransformPlugin, {
            getTargetClassName: function () {
                return targetClass;
            },
            transform: function (cm, cb) {
                plugin1Executed = true;
                console.log("  -> [Pipeline 1] JS Plugin 1 成功截获类: " + cm.thisClass().asInternalName());
            }
        });

        // 2. 实现第二个微插件（验证流水线连续性）
        const Plugin2 = Java.extend(ClassTransformPlugin, {
            getTargetClassName: function () {
                return targetClass;
            },
            transform: function (cm, cb) {
                plugin2Executed = true;
                console.log("  -> [Pipeline 2] JS Plugin 2 成功接收上游字节码并执行变换");
            }
        });

        orchestrator.registerPlugin(new Plugin1());
        orchestrator.registerPlugin(new Plugin2());

        // 3. 校验精准路由与查询
        const registered = orchestrator.getPluginsFor(targetClass);
        if (registered.size() !== 2) {
            throw new Error(`目标类注册插件数异常: 预期 2, 实际为 ${registered.size()}`);
        }
        const unrelated = orchestrator.getPluginsFor("game/unrelated/Block");
        if (!unrelated.isEmpty()) {
            throw new Error(`无关类不应匹配到插件, 实际匹配数: ${unrelated.size()}`);
        }
        console.log("✔ [2/4] 基于目标类名哈希精准路由与管道注册校验通过 (匹配数: 2)");

        // 4. 模拟类加载转换（使用 Logger.class 字节码作为真实输入）
        const Logger = Java.type("com.graalagent.util.Logger");
        const inStream = Logger.class.getClassLoader().getResourceAsStream("com/graalagent/util/Logger.class");
        const sampleBytes = inStream.readAllBytes();
        inStream.close();

        // 验证无关类不被修改，直接返回 null
        const untouched = orchestrator.transform(null, "game/unrelated/Block", null, null, sampleBytes);
        if (untouched !== null) {
            throw new Error("未注册插件的类 transform 应当返回 null，避免额外字节码拷贝");
        }

        // 验证系统内部类自动豁免
        const jdkExempt = orchestrator.transform(null, "java/lang/String", null, null, sampleBytes);
        if (jdkExempt !== null) {
            throw new Error("JDK 内部类应当直接豁免返回 null");
        }
        console.log("✔ [3/4] 未匹配类零开销透传 (null) 与 JDK/Agent 内部类豁免校验通过");

        // 验证目标类触发多插件管道链式执行
        const transformed = orchestrator.transform(null, targetClass, null, null, sampleBytes);
        if (!plugin1Executed || !plugin2Executed) {
            throw new Error(`流水线执行不完整: plugin1=${plugin1Executed}, plugin2=${plugin2Executed}`);
        }
        if (transformed === null || transformed.length === 0) {
            throw new Error("目标类变换后字节码为空");
        }
        console.log("✔ [4/4] 多插件顺序流水线链式变换执行成功 (输出字节大小: " + transformed.length + ")");
        console.log("==================================================");
        console.log("🎉 SMVOrchestrator 调度网格在 JavaScript 环境下验证全部通过！");
        console.log("==================================================");
        return true;
    } catch (e) {
        console.error("❌ SMVOrchestrator 验证失败:", e);
        if (e.printStackTrace) e.printStackTrace();
        return false;
    }
}

// 立即运行一次验证
runOrchestratorVerification();

// 若在游戏 / Agent 环境中运行，注册 /testorchestrator 快捷指令
if (typeof agent !== "undefined" && agent && agent.registerCommand) {
    agent.registerCommand("testorchestrator", (args) => {
        agent.chat("§6[Orchestrator-Verify] 正在执行 SMVOrchestrator 网格流水线验证...");
        const success = runOrchestratorVerification();
        if (success) {
            agent.chat("§a[Orchestrator-Verify] ✔ SMVOrchestrator 调度网格验证通过！支持 JS 动态插件精准路由与多阶段链式转换。");
        } else {
            agent.chat("§c[Orchestrator-Verify] ❌ 验证失败，请查看控制台日志。");
        }
    });
}
