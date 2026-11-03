package com.graalagent.concurrency;

public class ConcurrencyController {
    public boolean tryExecuteTickRead(Runnable tickAction) {
        // Red phase stub: always returns false without running action
        return false;
    }

    public void executeExclusiveReload(Runnable reloadAction) {
        // Red phase stub: does nothing
    }

    public boolean isWriteLocked() {
        return false;
    }

    public int getReadLockCount() {
        return 0;
    }
}
