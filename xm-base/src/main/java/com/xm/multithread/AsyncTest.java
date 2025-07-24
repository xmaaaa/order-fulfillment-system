package com.xm.multithread;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;

/**
 * @author hongwan
 * @date 2023/2/21
 */
public class AsyncTest {

    public static void main(String[] args) {
        forkJoinTest();
    }

    private static void completableTest() {
        List<CompletableFuture<Void>> futureList = Lists.newArrayList();
        List<Integer> result = Lists.newArrayList();
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            // 默认用的是forkJoinPool
            futureList.add(CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(finalI * 1000);
                } catch (InterruptedException e) {
                    throw new CompletionException(e);
                }
                System.out.println("第" + finalI);
                result.add(finalI);
                if (finalI == 5) {
                    throw new CompletionException(new Exception("111"));
                }
            }));
        }

        // 全部执行完才抛异常
//        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();
//        System.out.println(result);
        // 一个失败就抛异常
        try {
            futureList.forEach(CompletableFuture::join);
        } catch (CompletionException e) {
            System.out.println("报错了" + e.getMessage());
        }
        System.out.println(result);
    }

    public static void forkJoinTest() {
        // 默认用forkJoin
        IntStream.range(1, 100).parallel().forEach(i -> {
            System.out.println(Thread.currentThread().getName() + " -> " + i);
        });
    }
}
