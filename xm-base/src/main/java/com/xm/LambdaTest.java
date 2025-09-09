package com.xm;

import java.util.Comparator;

/**
 * @author XM
 * @date 2025/9/8
 */
public class LambdaTest {

    public static void main(String[] args) {
        // 语法糖: 类似可以自动实现接口
        Comparator<Integer> comparator = (o1, o2) -> o1 - o2;

    }
}
