package com.xm.sort;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author hongwan
 * @date 2022/9/27
 */
public class Compare {

    public static void main(String[] args) {
        List<Data> list = Lists.newArrayList();
        list.add(new Data(2));
        list.add(new Data(1));
        list.add(new Data(4));
        list.add(new Data(3));
        list.add(new Data(0));
        Collections.sort(list);
        list.forEach(l -> System.out.println(l.val));
        List<Data2> list2 = Lists.newArrayList();
        list2.add(new Data2(2));
        list2.add(new Data2(1));
        list2.add(new Data2(4));
        list2.add(new Data2(3));
        list2.add(new Data2(0));
        list2.sort(new Data2Comparator());
        list2.forEach(l -> System.out.println(l.val));
        list2.sort(Comparator.comparing(o -> o.val > 0));
        list2.sort(Comparator.comparingInt(o -> o.val));
        list2.forEach(l -> System.out.println(l.val));
    }


    /**
     * 若一个类实现了Comparable接口，就意味着“该类支持排序”
     */
    @lombok.Data
    @AllArgsConstructor
    static class Data implements Comparable<Data> {
        int val;

        @Override
        public int compareTo(Data o) {
            // 大于0, 则排在后面
            return val - o.val;
        }
    }

    @lombok.Data
    @AllArgsConstructor
    static class Data2 {
        int val;
    }

    /**
     * comparator意为可比较器, 可以对某个不可比较的类来定义比较规则
     */
    static class Data2Comparator implements Comparator<Data2> {

        @Override
        public int compare(Data2 o1, Data2 o2) {
            // 大于0, 则排在后面(交换顺序, 即o2 在前面)
            return o2.val - o1.val;
        }
    }
}
