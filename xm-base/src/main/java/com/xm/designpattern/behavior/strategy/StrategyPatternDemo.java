package com.xm.designpattern.behavior.strategy;

/**
 * 策略模式: 定义一系列的算法,把它们一个个封装起来, 并且使它们可相互替换。
 * 1. 将这些算法封装成一个一个的类，任意地替换。可以单独创建, 也可以和工厂模式结合
 * 2. 将复杂的算法与业务逻辑分离, 面向接口编程
 * 缺点: 策略类可能越来越多, 也需要调用方区分各种策略类
 * <p>
 * 区别: 策略模式核心在使用者的策略，按照自己的策略去替换。模板方法模式核心在子类的怎么实现, 定义好骨架
 *
 * @author XM
 * @date 2023/1/10
 */
public class StrategyPatternDemo {

    public static void main(String[] args) {

        // 切换策略可以手动，也可以自动，不限制方式。
        // 关键是上下文无需关心具体策略实现，只负责调用。
        Context context = new Context(new OperationAdd());
        System.out.println("10 + 5 = " + context.executeStrategy(10, 5));

        context = new Context(new OperationSubtract());
        System.out.println("10 - 5 = " + context.executeStrategy(10, 5));

        context = new Context(new OperationMultiply());
        System.out.println("10 * 5 = " + context.executeStrategy(10, 5));
    }
}