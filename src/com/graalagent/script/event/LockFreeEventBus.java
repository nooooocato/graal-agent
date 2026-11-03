package com.graalagent.script.event;

public class LockFreeEventBus {

    public enum EventPriority {
        LOWEST,
        LOW,
        NORMAL,
        HIGH,
        HIGHEST,
        MONITOR
    }

    public interface Cancellable {
        boolean isCancelled();
        void setCancelled(boolean cancelled);
    }

    @FunctionalInterface
    public interface EventListener<T> {
        void onEvent(T event) throws Exception;
    }

    public <T> void register(Class<T> eventType, EventPriority priority, EventListener<T> listener) {
        // Red phase stub
    }

    public <T> void register(Class<T> eventType, EventListener<T> listener) {
        register(eventType, EventPriority.NORMAL, listener);
    }

    public <T> void unregister(Class<T> eventType, EventListener<T> listener) {
        // Red phase stub
    }

    public <T> boolean emit(T event) {
        // Red phase stub: returns false without dispatching
        return false;
    }

    public void clear() {
        // Red phase stub
    }
}
