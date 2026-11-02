package com.graalagent.transformer;

import com.graalagent.script.ScriptManager;
import com.graalagent.util.Logger;

import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 影子方法虚拟化网格与插件流水线调度器。
 * 负责取代旧版巨石单体 MinecraftTransformer，基于目标类名哈希路由分发插件。
 */
public class SMVOrchestrator implements ClassFileTransformer {

    private final ClassFile classFile = ClassFile.of();
    private final Map<String, List<ClassTransformPlugin>> plugins = new ConcurrentHashMap<>();

    public SMVOrchestrator() {
    }

    /**
     * 注册微插件到调度器
     */
    public void registerPlugin(ClassTransformPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String targetName = normalizeClassName(plugin.getTargetClassName());
        if (targetName == null || targetName.isEmpty()) {
            throw new IllegalArgumentException("Target class name cannot be null or empty");
        }
        plugins.computeIfAbsent(targetName, k -> new CopyOnWriteArrayList<>()).add(plugin);
    }

    /**
     * 获取指定类名下注册的所有插件列表
     */
    public List<ClassTransformPlugin> getPluginsFor(String className) {
        String normalized = normalizeClassName(className);
        if (normalized == null) {
            return Collections.emptyList();
        }
        List<ClassTransformPlugin> list = plugins.get(normalized);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (className == null) {
            return null;
        }

        String normalizedName = normalizeClassName(className);

        // 避免插桩 JDK / GraalVM / Agent 内部运行时类
        if (isInternalClass(normalizedName)) {
            return null;
        }

        byte[] currentBytes = classfileBuffer;

        try {
            // 1. 查找并链式执行针对当前类的所有 SMV 微插件
            List<ClassTransformPlugin> targetPlugins = plugins.get(normalizedName);
            if (targetPlugins != null && !targetPlugins.isEmpty()) {
                for (ClassTransformPlugin plugin : targetPlugins) {
                    currentBytes = applyPlugin(plugin, currentBytes);
                }
            }

            // 2. 委托执行动态 JavaScript 自定义转换器
            ScriptManager sm = ScriptManager.getInstance();
            if (sm != null) {
                byte[] customResult = sm.applyCustomTransformers(normalizedName, currentBytes);
                if (customResult != null) {
                    currentBytes = customResult;
                }
            }

            if (currentBytes != classfileBuffer) {
                return currentBytes;
            }
        } catch (Throwable t) {
            Logger.error("Failed to transform class via SMVOrchestrator: " + normalizedName, t);
        }

        return null;
    }

    private byte[] applyPlugin(ClassTransformPlugin plugin, byte[] inputBytes) {
        ClassModel cm = classFile.parse(inputBytes);

        ClassTransform customTransform = plugin.asClassTransform(cm);
        if (customTransform != null) {
            return classFile.transformClass(cm, customTransform);
        }

        // 默认模式：构建新类并执行插件自定义逻辑，自动保留原始类元素
        return classFile.build(cm.thisClass().asSymbol(), realBuilder -> {
            plugin.transform(cm, realBuilder);
            for (ClassElement ce : cm) {
                realBuilder.with(ce);
            }
        });
    }

    private static String normalizeClassName(String name) {
        if (name == null) return null;
        return name.replace('.', '/');
    }

    private static boolean isInternalClass(String className) {
        return className.startsWith("java/")
                || className.startsWith("jdk/")
                || className.startsWith("sun/")
                || className.startsWith("com/oracle/")
                || className.startsWith("org/graalvm/")
                || className.startsWith("com/graalagent/");
    }
}
