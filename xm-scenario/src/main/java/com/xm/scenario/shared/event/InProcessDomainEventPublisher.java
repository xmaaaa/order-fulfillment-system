package com.xm.scenario.shared.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内同步发布（单机/单测）。生产可换：Outbox 写库 + 定时扫表发 MQ，或直接发 MQ。
 *
 * @author eddiema
 */
public class InProcessDomainEventPublisher implements DomainEventPublisher {

    private final Map<String, List<DomainEventSubscriber>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(DomainEvent event) {
        List<DomainEventSubscriber> list = subscribers.get(event.getEventType());
        if (list != null) {
            for (DomainEventSubscriber s : new ArrayList<>(list)) {
                try {
                    s.onEvent(event);
                } catch (Exception e) {
                    // 不因一个订阅者失败影响其他；生产可记日志/死信
                }
            }
        }
    }

    @Override
    public void subscribe(String eventType, DomainEventSubscriber subscriber) {
        subscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(subscriber);
    }
}
