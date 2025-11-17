package com.xm.sort;

/**
 * 基础的排序算法
 * 若两个“相等”的元素在排序前后的相对顺序保持不变，则称该排序算法为“稳定的”。
 *
 * @author XM
 * @date 2025/11/12
 */
public class BaseSort {


    /**
     * 冒泡排序 - 稳定
     * 思路: 每轮把最大（或最小）值推到最终位置
     * <p>
     * 时间复杂度 O(n ^ 2), 最好O(n)
     * 空间复杂度 O(1)
     *
     * @param arr
     */
    public static void bubbleSort(int[] arr) {
        int n = arr.length;
        boolean swapped;
        for (int i = 0; i < n - 1; i++) {
            swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    swap(arr, j, j + 1);
                    swapped = true;
                }
            }
            // 若本趟没有发生交换，说明数组已排序，提前结束
            if (!swapped) {
                break;
            }
        }
    }

    /**
     * 选择排序 - 不稳定
     * <p>
     * 时间复杂度: O(n ^ 2)
     * 空间复杂度: O(1)
     *
     * @param arr
     */
    public static void selectionSort(int[] arr) {
        int n = arr.length;
        for (int i = 0; i < n - 1; i++) {
            int minIndex = i;
            for (int j = i + 1; j < n; j++) {
                if (arr[j] < arr[minIndex]) {
                    minIndex = j;
                }
            }
            swap(arr, i, minIndex);
        }
    }

    /**
     * 插入排序(稳定): 思路是保证a(0~i-1)是有序数组，然后将元素i插入到合适位置
     * 插入排序在往前找时确实类似“反方向冒泡”，但区别是它不每次都交换，而是用覆盖移动的方式为 key 腾位置，然后一次性插入到目标点。
     * 时间复杂度 O(n ^ 2), 最好O(n)
     * <p>
     * 空间复杂度: O(1)
     *
     * @param arr
     */
    public static void insertionSort(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            int key = arr[i];
            // 注意从末尾i-1开始判断
            int j = i - 1;
            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    /**
     * 希尔排序(不稳定): 插入排序的改良版，通过gap值来控制比较粒度
     * <p>
     * gap 大 → 粗排 → 大幅减少无序度
     * gap 小 → 细排 → 最后一步接近线性排序
     * 时间复杂度: 介于 O(n log n) 和 O(n^2) 之间，取决于增量序列
     * 空间复杂度: O(1)
     *
     * @param arr
     */
    public static void shellSort(int[] arr) {
        int n = arr.length;
        for (int gap = n / 2; gap > 0; gap /= 2) {
            // 注意i = gap, 最小到1的时候就是插入排序，可以和上面代码比对一下
            for (int i = gap; i < n; i++) {
                int key = arr[i];
                // 注意末尾是i - gap, 和插入排序对比品味
                // 如果是i, 就要 j >= gap && arr[j - gap] > key
                int j = i - gap;
                while (j >= 0 && arr[j] > key) {
                    arr[j + gap] = arr[j];
                    j -= gap;
                }
                arr[j + gap] = key;
            }
        }
    }


    private static void swap(int[] a, int i, int j) {
        int temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }
}
