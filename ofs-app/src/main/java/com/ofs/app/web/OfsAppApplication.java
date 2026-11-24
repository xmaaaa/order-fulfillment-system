package com.ofs.app.web;

import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;

/**
 * @author hongwan
 * @date 2023/1/17
 */
@SpringBootApplication(exclude = {
        RedissonAutoConfiguration.class,
        GroovyTemplateAutoConfiguration.class
})
@ComponentScan(basePackages = {
        "com.ofs"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class OfsAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfsAppApplication.class, args);
    }
}
