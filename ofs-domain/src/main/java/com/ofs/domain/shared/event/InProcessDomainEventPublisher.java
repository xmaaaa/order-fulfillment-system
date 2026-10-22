package com.ofs.domain.shared.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内同步发布（单机/单测）。<b>当前未装配成 bean，无人使用</b>——原因与启用时机见
 * {@link DomainEventPublisher} 的类注释。
 *
 * <p>同步挨个调订阅者，<b>没有任何持久化</b>：进程崩了未处理完的事件就没了。
 * 所以订阅者只能干"同库、同事务"的活（更新投影表、写审计表）；
 * <b>绝不能在订阅者里直接发 MQ</b>——那样就绕开了 outbox，丢掉「写库与发消息原子化」这个唯一保障。
 * 要出站就让一个订阅者去写 outbox 行，由 relay 投递。
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
