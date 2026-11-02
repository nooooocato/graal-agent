package com.graalagent.transformer.smv;

/**
 * 专用于动态字节码加载与类校验的 ClassLoader
 */
public class SMVDynamicClassLoader extends ClassLoader {

    public SMVDynamicClassLoader() {
        super(SMVDynamicClassLoader.class.getClassLoader());
    }

    public SMVDynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    public Class<?> defineDynamicClass(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }
}
