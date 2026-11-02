package com.graalagent.transformer.smv;

/**
 * 生产环境与脚本验证通用的 SMV 探测样本类。
 * 包含标准实例方法、静态方法与带异常表方法，供自动化单测与 JavaScript 运行时验证。
 */
public class SMVProbeTarget {
    public static int hookCounter = 0;

    public String greet(String name) {
        return "Original: " + name;
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public String parseWithTryCatch(String text) {
        try {
            int val = Integer.parseInt(text);
            return "Parsed: " + val;
        } catch (NumberFormatException e) {
            return "Caught: " + e.getClass().getSimpleName();
        }
    }
}
