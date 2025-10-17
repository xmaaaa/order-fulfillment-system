package com.xm.multithread;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * @author XM
 * @date 2025/10/7
 */
public class AqsTest {

    public static void main(String[] args) {

        // 信号量，允许最多n个线程同时执行任务, 支持公平非公平
        Semaphore semaphore = new Semaphore(5, true);
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            new Thread(() -> {
                try {
                    semaphore.acquire();
                    // 执行任务
                    System.out.println("执行任务" + finalI);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();
                }
            }).start();
        }

        // 循环屏障, 等待全部就绪然后signalAll, 可复用
        CyclicBarrier cyclicBarrier = new CyclicBarrier(5, () -> System.out.println("全部就绪"));
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 3000));
                    System.out.println(Thread.currentThread().getName() + "加载完毕！");
                    cyclicBarrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    System.out.println(e.getMessage());
                }
            }, "水友" + i).start();
        }


        // 计数器为 3，表示需要等待 3 个任务完成
        CountDownLatch latch = new CountDownLatch(3);
        // 启动 3 个线程来执行任务
        for (int i = 1; i <= 3; i++) {
            new Thread(() -> {
                System.out.println(Thread.currentThread().getName() + " 执行任务");
                try {
                    Thread.sleep((long) (Math.random() * 3000));
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }
                // 每个线程执行完任务后递减计数器, 和cyclicBarrier区别在于会继续执行，用state等于0控制
                latch.countDown();
            }).start();
        }

        System.out.println("等待所有任务完成...");
        try {
            // 主线程等待所有任务完成
            latch.await();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
        System.out.println("所有任务已完成，继续执行主线程");
    }

}
