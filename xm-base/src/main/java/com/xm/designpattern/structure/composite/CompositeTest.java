package com.xm.designpattern.structure.composite;

/**
 * 组合模式使得用户对单个对象和组合对象的使用具有一致性。
 * 组合模式的本质：统一叶子对象和组合对象。
 * 组合模式的目的：让客户端不再区分操作的是组合对象还是叶子对象，而是以一个统一的方式来操作。
 *
 * @author hongwan
 * @date 2023/1/4
 */
public class CompositeTest {

    public static void main(String[] args) {
        // 让所有服装都统一结构，无论是叶子结点还是组合节点
        Composite root = new Composite("服装");
        Composite c1 = new Composite("男装");
        Composite c2 = new Composite("女装");

        Leaf leaf1 = new Leaf("衬衫");
        Leaf leaf2 = new Leaf("夹克");
        Leaf leaf3 = new Leaf("裙子");
        Leaf leaf4 = new Leaf("套装");

        root.addChild(c1);
        root.addChild(c2);
        c1.addChild(leaf1);
        c1.addChild(leaf2);
        c2.addChild(leaf3);
        c2.addChild(leaf4);

        root.printStruct("");
    }

}
