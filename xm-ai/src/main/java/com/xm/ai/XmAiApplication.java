package com.xm.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 订单智能助手启动类。
 * 这是一个独立的 Spring Boot 应用（和订单主系统分开部署）。
 */
@SpringBootApplication
public class XmAiApplication {
    public static void main(String[] args) {

        SpringApplication.run(XmAiApplication.class, args);
    }
}
