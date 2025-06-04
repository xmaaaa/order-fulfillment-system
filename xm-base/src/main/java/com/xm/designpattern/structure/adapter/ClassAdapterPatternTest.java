package com.xm.designpattern.structure.adapter;


/**
 * 适配器模式
 * 将一个类的接口转换成客户希望的另外一个接口。适配器模式使得原本由于接口不兼容而不能在一起工作的那些类可以一起工作。
 *
 * @author XM
 * @date 2023/1/4
 */
public class ClassAdapterPatternTest {

    public static void main(String[] args) {
        // 第一种方式，直接继承
        PowerTarget target = new PowerAdapter();
        target.output5V();
        // 第二种方式，持有
        PowerAdaptee powerAdaptee = new PowerAdaptee();
        PowerTarget target2 = new PowerAdapter2(powerAdaptee);
        target2.output5V();
    }
}