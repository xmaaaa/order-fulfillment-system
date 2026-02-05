package com.xm.scenario.shared.event;

import java.time.Instant;

/**
 * 领域事件标记接口。大厂常见：事件溯源、下游解耦、审计。
 *
 * @author eddiema
 */
public interface DomainEvent {

    /** 事件唯一 ID（用于去重、追踪） */
    String getEventId();

    /** 聚合根 ID（如 orderId） */
    String getAggregateId();

    /** 事件发生时间 */
    Instant getOccurredAt();

    /** 事件类型（用于路由、序列化） */
    String getEventType();
}
