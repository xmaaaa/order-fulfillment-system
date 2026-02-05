package com.xm.scenario.shared.event;

import java.time.Instant;

/**
 * 订单领域事件（OrderCreated / OrderSubmitted / OrderPaid / OrderShipped / OrderCancelled）。
 * 下游可订阅：履约、积分、风控、报表。
 */
public record OrderDomainEvent(
        String eventId,
        String orderId,
        String eventType,
        String userId,
        String payload,
        Instant occurredAt
) implements DomainEvent {

    public static final String TYPE_CREATED = "OrderCreated";
    public static final String TYPE_SUBMITTED = "OrderSubmitted";
    public static final String TYPE_PAID = "OrderPaid";
    public static final String TYPE_SHIPPED = "OrderShipped";
    public static final String TYPE_CANCELLED = "OrderCancelled";

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public String getAggregateId() {
        return orderId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getEventType() {
        return eventType;
    }
}
