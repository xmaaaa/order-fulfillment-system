package com.xm.util;

import com.xm.service.TestService;
import org.springframework.beans.factory.FactoryBean;


/**
 * FactoryBean 实现，用于自定义 MyService 的创建
 * 通过 FactoryBean 可以更自由的创建bean对象，spring先把factoryBean 放入bean，再把getObject的放入bean
 * 如果我自己想把代理对象或者复杂逻辑加入到bean中，我就需要自己管理创建bean逻辑，并且注入到容器，依赖等等，很复杂
 *
 *
 * @author xm
 */
public class MyServiceFactoryBean implements FactoryBean<TestService> {

    @Override
    public TestService getObject() {
        System.out.println("FactoryBean is creating MyService instance");
        // 这里可以复杂逻辑
        return new TestService();
    }

    @Override
    public Class<?> getObjectType() {
        return TestService.class;
    }
}
