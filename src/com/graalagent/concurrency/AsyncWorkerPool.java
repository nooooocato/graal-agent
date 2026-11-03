package com.graalagent.concurrency;

import com.graalagent.engine.ExecutionResult;
import com.graalagent.util.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 异步工作线程池 (AsyncWorkerPool)
 * 采用独立守护工作线程池（支持虚拟线程 / 守护工作线程），将玩家指令与耗时任务剥离 Minecraft 主循环。
 * 每一个异步执行任务均绑定 WatchdogTimer 超时看护令牌，超时即强制熔断。
 */
public class AsyncWorkerPool {
    private static volatile AsyncWorkerPool instance;

    private final ExecutorService executor;

    public AsyncWorkerPool() {
        ThreadFactory daemonFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "graal-async-worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
        this.executor = Executors.newCachedThreadPool(daemonFactory);
    }

    public static AsyncWorkerPool getInstance() {
        if (instance == null) {
            synchronized (AsyncWorkerPool.class) {
                if (instance == null) {
                    instance = new AsyncWorkerPool();
                }
            }
        }
        return instance;
    }

    public static void setInstance(AsyncWorkerPool pool) {
        instance = pool;
    }

    /**
     * 提交并在指定 Context 中异步执行 JavaScript 脚本求值，绑定 Watchdog 超时看护
     *
     * @param context 要执行的 Polyglot 容器
     * @param script  要执行的代码
     * @param timeout 超时预算（若为 null 则使用默认 50ms）
     * @return CompletableFuture 包含执行结果
     */
    public CompletableFuture<ExecutionResult> submitScript(Context context, String script, Duration timeout) {
        CompletableFuture<ExecutionResult> future = new CompletableFuture<>();
        if (context == null) {
            future.complete(ExecutionResult.failure(new IllegalStateException("Context is null"), 0));
            return future;
        }

        executor.submit(() -> {
            WatchdogTimer.WatchdogToken token = WatchdogTimer.schedule(context, timeout);
            long start = System.currentTimeMillis();
            try {
                Value result = context.eval("js", script);
                long elapsed = System.currentTimeMillis() - start;
                token.cancel();

                Object res = null;
                if (result != null && !result.isNull()) {
                    if (result.isHostObject()) {
                        res = result.asHostObject();
                    } else {
                        res = result.toString();
                    }
                }
                future.complete(ExecutionResult.success(res, elapsed));
            } catch (PolyglotException pe) {
                long elapsed = System.currentTimeMillis() - start;
                boolean timedOut = pe.isInterrupted() || pe.isCancelled() || token.hasTriggered();
                if (timedOut) {
                    Logger.warn("[AsyncWorker] 脚本执行超时触发安全熔断 (耗时 " + elapsed + "ms): " + pe.getMessage());
                    future.complete(ExecutionResult.timeout(elapsed, pe));
                } else {
                    Logger.warn("[AsyncWorker] 脚本抛出异常: " + pe.getMessage());
                    future.complete(ExecutionResult.failure(pe, elapsed));
                }
            } catch (Throwable t) {
                long elapsed = System.currentTimeMillis() - start;
                boolean timedOut = token.hasTriggered();
                if (timedOut) {
                    future.complete(ExecutionResult.timeout(elapsed, t));
                } else {
                    future.complete(ExecutionResult.failure(t, elapsed));
                }
            }
        });

        return future;
    }

    /**
     * 提交普通后台任务执行
     */
    public <T> CompletableFuture<T> submitTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    /**
     * 提交普通后台 Runnable 任务
     */
    public CompletableFuture<Void> submitTask(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
