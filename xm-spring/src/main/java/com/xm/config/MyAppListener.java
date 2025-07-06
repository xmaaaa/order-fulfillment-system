package com.xm.config;

import lombok.NonNull;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @author xm
 */
@Component
public class MyAppListener implements ApplicationListener<ApplicationStartedEvent> {

    @Override
    public void onApplicationEvent(@NonNull ApplicationStartedEvent event) {
        System.out.println("应用已启动！");
    }
}
