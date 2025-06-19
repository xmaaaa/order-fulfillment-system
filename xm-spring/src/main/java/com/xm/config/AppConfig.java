package com.xm.config;

import com.xm.util.MyServiceFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author xm
 */
@Configuration
public class AppConfig {

    @Bean
    public MyServiceFactoryBean testService() {
        return new MyServiceFactoryBean();
    }
}
