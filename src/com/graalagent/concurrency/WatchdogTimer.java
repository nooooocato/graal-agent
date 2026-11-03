package com.graalagent.concurrency;

import com.graalagent.util.Logger;
import org.graalvm.polyglot.Context;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 看门狗高精度硬中断定时器 (WatchdogTimer)
 * 在异步任务派发时绑定看护令牌，若任务超过时间预算（默认 50ms / 1 Tick），
 * 看门狗线程立即调用 context.interrupt(Duration.ZERO) 强制中断 Truffle 虚拟机死循环；
 * 若中断后依然超时未释放，调用 context.close(true) 强制自愈销毁。
 */
public final class WatchdogTimer {
    /** 默认主刻时限预算: 50 毫秒 */
    public static final Duration DEFAULT_BUDGET = Duration.ofMillis(50);
    /** 二级强制关闭缓冲时限: 50 毫秒 */
    public static final Duration FORCED_CLOSE_GRACE = Duration.ofMillis(50);

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "graal-watchdog-timer");
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        return t;
    });

    public interface WatchdogToken {
        /** 注销/取消看护令牌 */
        void cancel();
        /** 是否已触发看门狗熔断 */
        boolean hasTriggered();
    }

    private static class WatchdogTokenImpl implements WatchdogToken {
        private final Context context;
        private final AtomicBoolean triggered = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> interruptFuture;
        private volatile ScheduledFuture<?> forceCloseFuture;

        public WatchdogTokenImpl(Context context) {
            this.context = context;
        }

        public void setFutures(ScheduledFuture<?> interruptFuture, ScheduledFuture<?> forceCloseFuture) {
            this.interruptFuture = interruptFuture;
            this.forceCloseFuture = forceCloseFuture;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            if (interruptFuture != null) {
                interruptFuture.cancel(false);
            }
            if (forceCloseFuture != null) {
                forceCloseFuture.cancel(false);
            }
        }

        @Override
        public boolean hasTriggered() {
            return triggered.get();
        }

        void triggerInterrupt() {
            if (cancelled.get()) return;
            triggered.set(true);
            Logger.warn("[Watchdog] 脚本执行超时，正在触发 context.interrupt(Duration.ZERO)...");
            try {
                context.interrupt(Duration.ZERO);
            } catch (Throwable t) {
                Logger.error("[Watchdog] 触发 context.interrupt 异常", t);
            }
        }

        void triggerForceClose() {
            if (cancelled.get()) return;
            Logger.warn("[Watchdog] 脚本中断后未响应，正在强制执行 context.close(true)...");
            try {
                context.close(true);
            } catch (Throwable t) {
                Logger.error("[Watchdog] 强制关闭 Context 异常", t);
            }
        }
    }

    /**
     * 向高精度看门狗注册监听任务
     *
     * @param context 要守护的 Polyglot 容器
     * @param budget  执行时限预算 (超期即触发 context.interrupt)
     */
    public static WatchdogToken schedule(Context context, Duration budget) {
        if (context == null) {
            return new WatchdogToken() {
                @Override public void cancel() {}
                @Override public boolean hasTriggered() { return false; }
            };
        }

        Duration actualBudget = (budget != null && !budget.isNegative() && !budget.isZero()) ? budget : DEFAULT_BUDGET;
        WatchdogTokenImpl token = new WatchdogTokenImpl(context);

        ScheduledFuture<?> interruptFuture = SCHEDULER.schedule(
                token::triggerInterrupt,
                actualBudget.toMillis(),
                TimeUnit.MILLISECONDS
        );

        ScheduledFuture<?> forceCloseFuture = SCHEDULER.schedule(
                token::triggerForceClose,
                actualBudget.toMillis() + FORCED_CLOSE_GRACE.toMillis(),
                TimeUnit.MILLISECONDS
        );

        token.setFutures(interruptFuture, forceCloseFuture);
        return token;
    }

    public static void shutdown() {
        SCHEDULER.shutdownNow();
    }
}
