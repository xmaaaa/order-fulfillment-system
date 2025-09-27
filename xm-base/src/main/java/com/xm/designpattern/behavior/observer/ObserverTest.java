package com.xm.designpattern.behavior.observer;

/**
 * 观察者模式, 当一个对象的状态发生改变时，所有依赖于它的对象都得到通知并被自动更新。
 * 1、观察者和被观察者是抽象耦合的。
 * 2、建立一套触发机制。
 *
 * @author XM
 * @date 2023/1/11
 **/
public class ObserverTest {

    public static void main(String[] args) {
        // 创建被观察者
        WeatherDataSubject weatherData = new WeatherDataSubject();

        // 注册观察者到被观察者
        weatherData.addObserver(new BaiduSiteObserver());
        // 函数式编程写法
        weatherData.addObserver((t, p, h) -> System.out.println("---自定义显示--- 温度：" + t + " 气压：" + p + " 湿度：" + h));

        // 测试
        System.out.println("通知观察者，注意查看天气信息！");
        weatherData.setMeasurements(10F, 100F, 30.5F);
    }
}

