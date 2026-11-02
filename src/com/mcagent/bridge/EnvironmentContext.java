package com.mcagent.bridge;

public class EnvironmentContext {
    private final Object clientInstance;
    private final Object serverInstance;

    public EnvironmentContext(Object clientInstance, Object serverInstance) {
        this.clientInstance = clientInstance;
        this.serverInstance = serverInstance;
    }

    public EnvironmentType getEnvironmentType() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean isClient() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean isDedicatedServer() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void execute(Runnable runnable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
