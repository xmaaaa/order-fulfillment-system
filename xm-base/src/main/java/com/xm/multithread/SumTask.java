package com.xm.multithread;

import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

/**
 * ForkJoinPool 分而治之思想用法，任务拆分和合并
 *
 * @author XM
 * @date 2025/10/8
 */
public class SumTask extends RecursiveTask<Integer> {
    private final int start, end;
    private static final int THRESHOLD = 10;

    public SumTask(int start, int end) {
        this.start = start;
        this.end = end;
    }

    @Override
    protected Integer compute() {
        // 小任务，直接计算
        if (end - start <= THRESHOLD) {
            int sum = 0;
            for (int i = start; i <= end; i++) {
                sum += i;
            }
            return sum;
        }
        // 大任务，分成两半
        int mid = (start + end) / 2;
        SumTask left = new SumTask(start, mid);
        SumTask right = new SumTask(mid + 1, end);
        left.fork();                  // 异步执行左任务
        // 当前线程执行右任务
        int rightResult = right.compute();
        // 等待左任务结果
        int leftResult = left.join();
        // 汇总
        return leftResult + rightResult;
    }

    public static void main(String[] args) {
        int result;
        try (ForkJoinPool pool = new ForkJoinPool()) {
            result = pool.invoke(new SumTask(1, 100));
        }
        System.out.println("Total sum = " + result);
    }
}
