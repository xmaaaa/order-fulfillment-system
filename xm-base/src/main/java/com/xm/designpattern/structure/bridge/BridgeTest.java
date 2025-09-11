package com.xm.designpattern.structure.bridge;

/**
 * 桥接模式
 * 优点:
 * 桥接模式可以极大的减少子类的个数，从而降低管理和维护的成本。
 * 极大的提高了系统可扩展性，在两个变化维度中任意扩展一个维度，都不需要修改原有的系统，符合开闭原则。
 * 例子: 人(男人，女人)穿衣服(夹克，裤子)，如果不用这种方式，就会有男人夹克，男人裤子，女人裤子的类。。。
 * 缺点:
 * 会增加系统的理解与设计难度，由于关联关系建立在抽象层，要求开发者一开始就针对抽象层进行设计与编程
 *
 * @author hongwan
 * @date 2022/12/23
 */
public class BridgeTest {

    public static void main(String[] args) {

        Person man = new Man();
        Person lady = new Lady();

        Clothing jacket = new Jacket();
        Clothing trouser = new Trouser();

        man.setClothing(jacket);
        lady.setClothing(trouser);
        man.dress();
        lady.dress();
    }
}
