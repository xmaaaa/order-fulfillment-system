package com.xm.scenario.shared.event;

/**
 * 领域事件发布者。实现可选：内存同步、TransactionalOutbox、MQ。
 * 大厂常见：发布后异步投递 MQ，下游幂等消费。
 *
 * @author eddiema
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    /** 订阅（内存实现用；Outbox/MQ 由下游消费） */
    void subscribe(String eventType, DomainEventSubscriber subscriber);

    @FunctionalInterface
    interface DomainEventSubscriber {
        void onEvent(DomainEvent event);
    }
}
