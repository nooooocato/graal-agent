package com.graalagent.transformer.plugins;

import com.graalagent.transformer.ClassTransformPlugin;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;

/**
 * 客户端数据包微插件：虚拟化 ClientPacketListener 的 sendChat、sendCommand 与 handleCommands
 */
public class PacketListenerPlugin implements ClassTransformPlugin {

    public static final String TARGET_CLASS = "net/minecraft/client/multiplayer/ClientPacketListener";

    @Override
    public String getTargetClassName() {
        return TARGET_CLASS;
    }

    @Override
    public void transform(ClassModel classModel, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException("PacketListenerPlugin is not implemented yet (Red Phase stub)");
    }
}
