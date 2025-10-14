package com.xm.util;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * 通过此工具类, 获取到spring bean.在一些线程和非spring管控的类型中,很方便
 *
 * @author XM
 */
@Component
public class SpringUtil implements ApplicationContextAware {

    @Getter
    private static ApplicationContext context;

    /**
     * 此方法可以把ApplicationContext对象inject到当前类中作为一个静态成员变量
     */
    @Override
    public void setApplicationContext(ApplicationContext context)
            throws BeansException {
        SpringUtil.context = context;
    }

    public static <T> T getBean(Class<T> clazz) {
        try {
            return getContext().getBean(clazz);
        } catch (Exception e) {
            // 捕捉异常
            return null;
        }
    }

    public static Object getBean(String beanId) {
        try {
            return getContext().getBean(beanId);
        } catch (Exception e) {
            // 捕捉异常
            return null;
        }
    }

    public static <T> T getBean(String beanId, Class<T> clazz) {
        try {
            return getContext().getBean(beanId, clazz);
        } catch (Exception e) {
            // 捕捉异常
            return null;
        }
    }
}
