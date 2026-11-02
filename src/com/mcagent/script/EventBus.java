package com.mcagent.script;

import com.mcagent.util.Logger;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private final Map<String, List<Value>> listeners = new ConcurrentHashMap<>();

    public void on(String eventName, Value callback) {
        if (callback != null && callback.canExecute()) {
            listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(callback);
        }
    }

    public void once(String eventName, Value callback) {
        if (callback != null && callback.canExecute()) {
            Value[] wrapper = new Value[1];
            wrapper[0] = Value.asValue((Runnable) () -> {
                off(eventName, wrapper[0]);
                callback.execute();
            });
            on(eventName, wrapper[0]);
        }
    }

    public void off(String eventName, Value callback) {
        List<Value> list = listeners.get(eventName);
        if (list != null) {
            list.remove(callback);
        }
    }

    public void onTick(Value callback) {
        on("tick", callback);
    }

    public void onKey(Value callback) {
        on("key", callback);
    }

    public void onChat(Value callback) {
        on("chat", callback);
    }

    public void onInit(Value callback) {
        on("init", callback);
    }

    public boolean emit(String eventName, Object... args) {
        List<Value> list = listeners.get(eventName);
        if (list == null || list.isEmpty()) {
            return false;
        }

        boolean handled = false;
        for (Value callback : list) {
            try {
                Value res = callback.execute(args);
                if (res != null && res.isBoolean() && res.asBoolean()) {
                    handled = true;
                }
            } catch (Throwable t) {
                Logger.error("Error executing listener for event '" + eventName + "'", t);
            }
        }
        return handled;
    }

    public void clear() {
        listeners.clear();
    }
}
