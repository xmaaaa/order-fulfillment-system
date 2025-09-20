package com.xm.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

/**
 * 自定义事件监听类，事件异步处理，由于没接口用cglib代理才会放入线程池
 *
 * @author XM
 * @date 2025/9/11
 */
@Component
@EnableAsync(proxyTargetClass = true)
public class MyEventListener {

    /**
     * 异步处理事件
     *
     * @param event
     */
    @Async
    @EventListener
    public void handleUserRegistered(MyEvent event) {
        System.out.println(Thread.currentThread().getId() + " 处理注册事件：" + event.getName());
    }
}
