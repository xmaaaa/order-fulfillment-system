package com.xm.designpattern.behavior.observer;


/**
 * 函数式接口，更新天气
 *
 * @author XM
 * @date 2025/9/10
 */
@FunctionalInterface
public interface WeatherObserver {

    void update(float temperature, float pressure, float humidity);
}
