package com.xm.designpattern.create;

/**
 * 单例模式, 六种方式
 *
 * @author hongwan
 * @date 2022/12/14
 */
public class Singleton {

    private static final Singleton instance1 = new Singleton();
    private static Singleton instance2;
    private static Singleton instance3;

    // 1. 分配内存空间。
    // 2. 初始化对象。
    // 3. 将对象指向内存地址。
    // 指令重排序，有可能32有了引用，但未初始化，如果没有 volatile 关键字，其他线程可能会拿到一个“未初始化完”的对象
    private volatile static Singleton instance4;

    private static class LazyHolder {
        private static final Singleton INSTANCE = new Singleton();
    }

    /**
     * 私有构造方法
     */
    private Singleton() {
    }

    /**
     * 饿汉模式!!!
     *
     * @return
     */
    public static Singleton getInstance() {
        return instance1;
    }

    /**
     * 懒汉模式（线程不安全）
     *
     * @return
     */
    public static Singleton getInstance2() {
        if (instance2 == null) {
            instance2 = new Singleton();
        }
        return instance2;
    }

    /**
     * 懒汉模式（线程安全）
     * 该方法使用synchronized加锁，来保证线程安全性
     * 缺点：每次调用都加锁同步执行，对象返回效率低，不推荐使用
     *
     * @return
     */
    public static synchronized Singleton getInstance3() {
        if (instance3 == null) {
            instance3 = new Singleton();
        }
        return instance3;
    }

    /**
     * 双重检索模式, 效率较高
     *
     * @return
     */
    public static Singleton getInstance4() {
        // 第一个 if 语句用来避免已经被实例化之后的加锁操作
        if (instance4 == null) {
            // 加锁
            synchronized (Singleton.class) {
                // 第二个 if 语句进行了加锁，所以只能有一个线程进入，就不会出现 instance == null 时两个线程同时进行实例化操作。
                if (instance4 == null) {
                    instance4 = new Singleton();
                }
            }
        }
        return instance4;
    }

    /**
     * 该模式利用了静态内部类延迟初始化的特性，来达到与双重校验锁方式一样的功能
     *
     * @return
     */
    public static Singleton getInstance5() {
        return LazyHolder.INSTANCE;
    }

    /**
     * 枚举类
     *
     * @return
     */
    public static Singleton getInstance6() {
        return null;
    }
}
