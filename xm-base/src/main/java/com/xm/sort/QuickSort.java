package com.xm.sort;

import java.util.Arrays;
import java.util.Random;

/**
 * 快速排序 不稳定
 * 时间复杂度 最好O(n * log n) 最差 O(n^2)
 * 空间复杂度 O(logn)
 *
 * @author hongwan
 * @date 2022/8/19
 */
public class QuickSort {
    private static final Random random = new Random();


    public static void main(String[] args) {
        int[] arr = new int[]{5, 4, 3, 52, 6, 7};
        quickSort(arr, 0, arr.length - 1);
        int quickSelect = quickSelect(arr, 0, arr.length - 1, 6);
        System.out.println(Arrays.toString(arr));
        System.out.println(quickSelect);
    }

    /**
     * 快速排序
     *
     * @param arr
     * @param start
     * @param end
     */
    private static void quickSort(int[] arr, int start, int end) {
        if (start >= end) {
            return;
        }
        int partition = randomPartition(arr, start, end);
        quickSort(arr, start, partition - 1);
        quickSort(arr, partition + 1, end);
    }


    /**
     * 快速选择 O(n)
     *
     * @param a
     * @param start
     * @param end
     * @param index
     * @return
     */
    public static int quickSelect(int[] a, int start, int end, int index) {
        if (start > index || end < index) {
            return -1;
        }
        int q = randomPartition(a, start, end);
        if (q == index) {
            return a[q];
        } else {
            return q < index ? quickSelect(a, q + 1, end, index) : quickSelect(a, start, q - 1, index);
        }
    }

    /**
     * 此处为了更随机，从数组中随便取一位数进行交换
     *
     * @param a
     * @param start
     * @param end
     * @return
     */
    public static int randomPartition(int[] a, int start, int end) {
        int i = random.nextInt(end - start + 1) + start;
        swap(a, i, start);
        return partition(a, start, end);
    }

    public static int partition(int[] a, int start, int end) {
        int left = start, right = end;
        while (left < right) {
            // 如果被作为比较值的数在起点，那么需要从终点开始，否则如果数组本身有序，就会被交换掉
            while (left < right && a[right] >= a[start]) {
                right--;
            }
            while (left < right && a[left] <= a[start]) {
                left++;
            }
            swap(a, left, right);
        }
        // 此处left 和right 相同, 换哪个都行
        swap(a, right, start);
        return left;
    }

    public static void swap(int[] a, int i, int j) {
        int temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }
}
