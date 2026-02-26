package com.xm.scenario.concurrent.lock;

/**
 * 全局加锁顺序常量，避免多粒度锁死锁。
 * 约定：ORDER -> INVENTORY -> USER -> THIRD_PARTY
 */
public final class LockOrder {

    public static final int ORDER = 100;
    public static final int INVENTORY = 200;
    public static final int USER = 300;
    public static final int THIRD_PARTY = 400;

    private LockOrder() {
    }
}
