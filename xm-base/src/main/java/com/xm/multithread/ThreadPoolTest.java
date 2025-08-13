package com.xm.multithread;

import java.time.LocalTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 推荐自己用 ThreadPoolExecutor 构造
 * 注意：Executors 工厂方法容易导致任务堆积、OOM，因为它的队列和线程数默认值不够安全！！下面只是为了书写方便
 *
 * @author XM
 * @date 2025/7/20
 */
public class ThreadPoolTest {


    public static void main(String[] args) {
        threadPool3();
    }

    /**
     * 模拟银行叫号场景
     */
    public static void threadPool1() {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (int i = 1; i <= 20; i++) {
            int customerId = i;
            executor.submit(() -> {
                System.out.println(Thread.currentThread().getName() + " 正在为客户 " + customerId + " 办理业务");
                try {
                    // 模拟办理业务时间
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }
            });
        }

        executor.shutdown();
    }

    /**
     * 限流控制（最多同时处理 10 个请求）
     */
    public static void threadPool2() {
        ExecutorService executor = Executors.newCachedThreadPool();
        Semaphore semaphore = new Semaphore(10);

        for (int i = 1; i <= 50; i++) {
            int userId = i;
            // 此时已经创建线程，因为用的是cached队列不存储，所以线程创建了50个
            // 如果使用fixed，则会复用线程
            executor.submit(() -> {
                try {
                    // 拿到“通行证”才能执行
                    semaphore.acquire();
                    System.out.println("用户 " + userId + " 正在访问接口（线程：" + Thread.currentThread().getName() + ")");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                } finally {
                    // 释放通行证
                    semaphore.release();
                }
            });
        }
        executor.shutdown();
    }

    /**
     * 定时线程池执行任务
     */
    public static void threadPool3() {
        // 核心线程数大于1，仍然优先分配用同一个执行
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        Runnable task = new Runnable() {
            int count = 0;

            @Override
            public void run() {
                System.out.println("执行时间：" + LocalTime.now());
                count++;
                if (count >= 5) {
                    scheduler.shutdown();
                }
            }
        };
        scheduler.scheduleAtFixedRate(task, 0, 2, TimeUnit.SECONDS);
    }
}
