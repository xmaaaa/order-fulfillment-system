package com.xm.string;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * KMP算法
 * 时间复杂度 O(n + m)
 *
 * @author XM
 * @date 2024/9/1
 */
public class KMP {


    /**
     * next 数组, 当前后缀和前缀相等的最长长度
     *
     * @param pattern
     * @return
     */
    public static int[] buildNext(String pattern) {
        int m = pattern.length();
        int[] next = new int[m];
        // 一定要是0，只考虑不包括自身的真前缀
        next[0] = 0;
        // 注意j 表示从j开始比较, 假设 j = 2, 代表pattern 0 ~ i - 1 和 pattern 0 ~ 1 相等
        int j = 0;
        // 注意要从1开始, 第一个值为0，否则会死循环
        for (int i = 1; i < m; i++) {
            // 当匹配不上了，j需要回退，其实和模式匹配逻辑一样，回退到上一个值
            while (j > 0 && pattern.charAt(i) != pattern.charAt(j)) {
                j = next[j - 1];
            }
            // 这里注意，表示当前后缀和前缀的 0 ~ j - 1 相等, 因为判断完才 + 1，相应的长度为j
            // 假设 j = 2, 代表当前相等长度为2，是0 ~ 1 和后缀相等
            if (pattern.charAt(i) == pattern.charAt(j)) {
                j++;
            }
            next[i] = j;
        }
        return next;
    }


    public static List<Integer> kmpSearch(String text, String pattern) {
        int n = text.length();
        int m = pattern.length();
        int[] next = buildNext(pattern);
        int j = 0;
        List<Integer> result = Lists.newArrayList();
        // 注意一个从0开始，这个算法类似上面，只不过j == m 时表示完全匹配了, 上面的方法从1开始不可能j = m
        for (int i = 0; i < n; i++) {
            // 这里非常难理解, 举个例子AABAAD 匹配 AABAAC, next数组为010120，j为5
            // 当最后一个C匹配失败, 表示AABAA匹配成功，肉眼很好理解我们从AA后面继续比，取值就是next[5 - 1] = 2, 也就是AA
            // 此时又失败, 但是能知道A匹配成功了，从A后面继续比，我们继续取值next[2 - 1] = 1
            while (j > 0 && text.charAt(i) != pattern.charAt(j)) {
                j = next[j - 1];
            }
            if (text.charAt(i) == pattern.charAt(j)) {
                j++;
            }
            if (j == m) {
                result.add(i - m + 1);
                j = next[j - 1];
            }
        }
        return result;
    }

    public static void main(String[] args) {
        String text = "ABABDABACDABABCABAB";
        String pattern = "ABAB";
        List<Integer> result = kmpSearch(text, pattern);
        System.out.println("Pattern found at index: " + result);
    }


}
