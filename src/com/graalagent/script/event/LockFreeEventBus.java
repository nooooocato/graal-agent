package com.graalagent.script.event;

import com.graalagent.util.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 无锁高并发事件总线 (LockFreeEventBus)：
 * 1. 核心读路径 (emit) 采用 CopyOnWriteArrayList 实现 100% 无锁并发遍历，彻底消除并发读写锁竞争。
 * 2. 支持严格的六级事件优先级调度 (HIGHEST -> HIGH -> NORMAL -> LOW -> LOWEST -> MONITOR)。
 * 3. 支持 Cancellable 事件短路机制：高优先级监听器取消事件后，普通与低优先级监听器自动跳过，
 *    仅 MONITOR 监听器可继续接收以完成最终审计与遥测。
 * 4. 监听器异常天然隔离，单个监听器抛出异常绝不打断同批次后续监听器与游戏主循环。
 */
public class LockFreeEventBus {

    public enum EventPriority {
        HIGHEST(0),
        HIGH(1),
        NORMAL(2),
        LOW(3),
        LOWEST(4),
        MONITOR(5);

        private final int order;

        EventPriority(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }
    }

    public interface Cancellable {
        boolean isCancelled();
        void setCancelled(boolean cancelled);
    }

    @FunctionalInterface
    public interface EventListener<T> {
        void onEvent(T event) throws Exception;
    }

    private static class Registration<T> {
        final EventPriority priority;
        final EventListener<T> listener;

        Registration(EventPriority priority, EventListener<T> listener) {
            this.priority = priority;
            this.listener = listener;
        }
    }

    private final Map<Class<?>, CopyOnWriteArrayList<Registration<?>>> listenersByClass = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Registration<Object[]>>> listenersByName = new ConcurrentHashMap<>();

    /**
     * 注册类型化事件监听器，指定优先级。
     */
    public synchronized <T> void register(Class<T> eventType, EventPriority priority, EventListener<T> listener) {
        if (eventType == null || listener == null) return;
        EventPriority prio = (priority != null) ? priority : EventPriority.NORMAL;
        CopyOnWriteArrayList<Registration<?>> list = listenersByClass.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        
        Registration<T> reg = new Registration<>(prio, listener);
        // 按优先级顺序插入 (HIGHEST -> MONITOR)
        int index = 0;
        for (Registration<?> existing : list) {
            if (prio.getOrder() < existing.priority.getOrder()) {
                break;
            }
            index++;
        }
        list.add(index, reg);
    }

    /**
     * 注册类型化事件监听器，使用默认 NORMAL 优先级。
     */
    public <T> void register(Class<T> eventType, EventListener<T> listener) {
        register(eventType, EventPriority.NORMAL, listener);
    }

    /**
     * 注销类型化事件监听器。
     */
    public synchronized <T> void unregister(Class<T> eventType, EventListener<T> listener) {
        if (eventType == null || listener == null) return;
        CopyOnWriteArrayList<Registration<?>> list = listenersByClass.get(eventType);
        if (list != null) {
            list.removeIf(reg -> reg.listener.equals(listener));
        }
    }

    /**
     * 注册具名字符串事件监听器。
     */
    public synchronized void registerNamed(String eventName, EventPriority priority, EventListener<Object[]> listener) {
        if (eventName == null || listener == null) return;
        EventPriority prio = (priority != null) ? priority : EventPriority.NORMAL;
        CopyOnWriteArrayList<Registration<Object[]>> list = listenersByName.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>());
        
        Registration<Object[]> reg = new Registration<>(prio, listener);
        int index = 0;
        for (Registration<Object[]> existing : list) {
            if (prio.getOrder() < existing.priority.getOrder()) {
                break;
            }
            index++;
        }
        list.add(index, reg);
    }

    /**
     * 无锁并发派发类型化事件。
     *
     * @param event 要派发的事件对象
     * @return true 若至少有一个监听器被成功触发
     */
    @SuppressWarnings("unchecked")
    public <T> boolean emit(T event) {
        if (event == null) return false;
        Class<?> eventClass = event.getClass();
        CopyOnWriteArrayList<Registration<?>> list = listenersByClass.get(eventClass);
        if (list == null || list.isEmpty()) {
            return false;
        }

        boolean handled = false;
        Cancellable cancellable = (event instanceof Cancellable c) ? c : null;

        for (Registration<?> rawReg : list) {
            Registration<T> reg = (Registration<T>) rawReg;
            // 短路已取消事件：除 MONITOR 优先级外，其余监听器全部跳过
            if (cancellable != null && cancellable.isCancelled() && reg.priority != EventPriority.MONITOR) {
                continue;
            }

            try {
                reg.listener.onEvent(event);
                handled = true;
            } catch (Throwable t) {
                Logger.error("Error executing listener in LockFreeEventBus for event " + eventClass.getSimpleName(), t);
                handled = true; // 即使单个监听器异常，仍记录已被处理
            }
        }

        return handled;
    }

    /**
     * 派发具名字符串事件。
     */
    public boolean emitNamed(String eventName, Object... args) {
        if (eventName == null) return false;
        CopyOnWriteArrayList<Registration<Object[]>> list = listenersByName.get(eventName);
        if (list == null || list.isEmpty()) {
            return false;
        }

        boolean handled = false;
        for (Registration<Object[]> reg : list) {
            try {
                reg.listener.onEvent(args);
                handled = true;
            } catch (Throwable t) {
                Logger.error("Error executing named listener in LockFreeEventBus for event '" + eventName + "'", t);
                handled = true;
            }
        }
        return handled;
    }

    /**
     * 清空所有注册的监听器。
     */
    public synchronized void clear() {
        listenersByClass.clear();
        listenersByName.clear();
    }
}
