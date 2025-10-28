package com.xm.designpattern.behavior.observer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气数据
 * 1.包含最新的天气情况信息
 * 2.带有观察者集合，使用ArrayList管理
 * 3.当数据有更新时，就主动的调用ArrayList，通知所有的（接入方）就可以看到最新的信息
 *
 * @author XM
 * @date 2023/1/11
 **/
@Data
public class WeatherDataSubject {
    private float temperature;
    private float humidity;
    private float pressure;

    private List<WeatherObserver> observers = new ArrayList<>();

    public void addObserver(WeatherObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(WeatherObserver observer) {
        observers.remove(observer);
    }

    /**
     * 设置测量数据
     *
     * @param temperature 温度
     * @param pressure    压力
     * @param humidity    湿度
     */
    public void setMeasurements(float temperature, float humidity, float pressure) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.pressure = pressure;
        notifyObservers();
    }

    private void notifyObservers() {
        for (WeatherObserver observer : observers) {
            observer.update(temperature, humidity, pressure);
        }
    }
}

