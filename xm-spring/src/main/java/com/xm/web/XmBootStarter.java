package com.xm.web;

import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author hongwan
 * @date 2023/1/17
 */
@SpringBootApplication(exclude = {RedissonAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.xm"
})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class XmBootStarter {

    public static void main(String[] args) {
        SpringApplication.run(XmBootStarter.class, args);
    }
}
