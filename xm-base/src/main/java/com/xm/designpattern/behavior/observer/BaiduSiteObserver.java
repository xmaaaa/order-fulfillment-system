package com.xm.designpattern.behavior.observer;

/**
 * 百度天气观察者
 *
 * @author xm
 * @date 2023/1/11
 **/
public class BaiduSiteObserver implements WeatherObserver {

    float temperature;
    float pressure;
    float humidity;

    public void display() {
        System.out.println("---百度天气-天气预报---");
        System.out.println("---百度天气 气温：" + temperature);
        System.out.println("---百度天气 气压：" + pressure);
        System.out.println("---百度天气 湿度：" + humidity);
    }

    /**
     * 更新天气状况
     *
     * @param temperature
     * @param pressure
     * @param humidity
     */
    @Override
    public void update(float temperature, float pressure, float humidity) {
        this.temperature = temperature;
        this.pressure = pressure;
        this.humidity = humidity;
        display();
    }
}

