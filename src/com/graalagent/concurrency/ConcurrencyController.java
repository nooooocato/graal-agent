package com.graalagent.concurrency;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * 读写锁解耦控制器：分离高频主线程心跳读锁与低频热重载排他写锁。
 * 在热重载期间，游戏主线程尝试获取读锁若失败则直接跳过，绝不阻塞游戏主线程。
 */
public class ConcurrencyController {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    /**
     * 主线程游戏心跳非阻塞读操作。
     * 若恰逢热重载持有写锁，立即返回 false 跳过本次心跳，保证游戏主线程零等待。
     *
     * @param tickAction 刻任务
     * @return true 若成功获取读锁并执行；false 若锁被占用直接跳过
     */
    public boolean tryExecuteTickRead(Runnable tickAction) {
        if (!readLock.tryLock()) {
            return false;
        }
        try {
            tickAction.run();
            return true;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 阻塞式共享读操作（用于普通读、指令评估或事件派发）。
     *
     * @param readAction 读任务
     */
    public void executeRead(Runnable readAction) {
        readLock.lock();
        try {
            readAction.run();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 阻塞式共享读操作并返回结果。
     *
     * @param readSupplier 读任务供应器
     * @param <T> 返回值类型
     * @return 读操作返回值
     */
    public <T> T executeReadWithResult(Supplier<T> readSupplier) {
        readLock.lock();
        try {
            return readSupplier.get();
        } finally {
            readLock.unlock();
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Throwable> {
        T get() throws E;
    }

    /**
     * 支持受检异常的阻塞式共享读操作。
     */
    public <T, E extends Throwable> T executeReadWithResultThrows(ThrowingSupplier<T, E> supplier) throws E {
        readLock.lock();
        try {
            return supplier.get();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 排他式写操作（用于热重载或销毁重建 Context）。
     *
     * @param reloadAction 重载任务
     */
    public void executeExclusiveReload(Runnable reloadAction) {
        writeLock.lock();
        try {
            reloadAction.run();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 是否当前正持有写锁（处于热重载阶段）。
     */
    public boolean isWriteLocked() {
        return rwLock.isWriteLocked();
    }

    /**
     * 当前活跃共享读锁数量。
     */
    public int getReadLockCount() {
        return rwLock.getReadLockCount();
    }
}
