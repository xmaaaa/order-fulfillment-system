package com.xm.util;

import com.xm.service.TestService;
import org.springframework.beans.factory.FactoryBean;


/**
 * FactoryBean 实现，用于自定义 MyService 的创建
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
