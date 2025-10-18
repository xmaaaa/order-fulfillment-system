package com.xm.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @author XM
 * @date 2025/9/11
 */
@Component
public class StartupListener implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Override
    // 或者 @EventListener(ApplicationReadyEvent.class)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 容器启动完成后执行
        publisher.publishEvent(new MyEvent("应用启动完成, 发布我的事件"));
    }
}
