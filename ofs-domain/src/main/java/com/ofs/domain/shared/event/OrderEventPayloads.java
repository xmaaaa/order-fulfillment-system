package com.ofs.domain.shared.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 构造订单事件的 outbox 消息体（JSON）。
 *
 * <p>字段：
 * <pre>
 *   schemaVersion  消息格式版本，见下
 *   eventId        本次投递的唯一 ID，只用于排查链路
 *   eventType      OrderCreated / OrderPaid / ...
 *   orderId        聚合根 ID，也是 Kafka 分区键
 *   userId         下游（通知服务）要用它找人，省一次回查
 *   version        订单聚合的乐观锁版本号，<b>流转之后</b>的值
 *   occurredAt     事件发生时间
 *   + 各事件自己的业务字段（如 OrderPaid 的 paymentId）
 * </pre>
 *
 * <h3>version 是干什么的</h3>
 * 给下游判断<b>陈旧</b>用的：收到 version 比本地已处理的还小，说明这是条迟到的旧消息，
 * 对「状态复制」类下游（读模型投影、ES 同步）可以直接丢弃，不必依赖 MQ 的顺序保证。
 *
 * <p>注意它<b>不能</b>用来跳过事件。本项目的消费者是反应型的（收到 OrderPaid 要发短信），
 * 哪怕 OrderShipped(version=3) 先到了，OrderPaid(version=2) 那条短信照样得发——
 * version 解决的是「别用旧状态覆盖新状态」，不是「少处理一条没关系」。
 *
 * <h3>schemaVersion 是干什么的</h3>
 * 消息格式是整个系统里<b>最难改</b>的东西：发布方和消费方独立部署、topic 里还积压着旧格式消息、
 * 已发出的消息无法随代码回滚，而且改错了没有编译期检查——运行时在别人的服务里炸。
 * 带上版本号，将来做破坏性变更时下游能分流处理，而不是解析失败。
 *
 * <p>改动纪律：<b>只加不删不改</b>。加可选字段是安全的（老消费者忽略它）；
 * 删字段、改名、改类型会让老消费者解析失败；最坏的是字段名和类型都没变、<b>语义</b>变了
 * （比如金额单位从元改成分），这种没有任何工具能拦住。真要破坏性变更，走新 topic + 双写。
 *
 * <p>eventId 每次调用都新生成，<b>下游去重别用它</b>——要用 {@code orderId + eventType}，
 * 那才是业务上的唯一标识（outbox 表的唯一索引也是按这两个字段建的）。
 */
public final class OrderEventPayloads {

    /**
     * 消息格式版本。破坏性变更时 +1，并且必须有一段新旧消费者共存的过渡期。
     * v1 = schemaVersion/eventId/eventType/orderId/userId/version/occurredAt + 业务字段。
     */
    public static final int SCHEMA_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OrderEventPayloads() {
    }

    public static String created(String orderId, String userId, long version) {
        return build(OrderDomainEvent.TYPE_CREATED, orderId, userId, version, Map.of());
    }

    public static String submitted(String orderId, String userId, long version) {
        return build(OrderDomainEvent.TYPE_SUBMITTED, orderId, userId, version, Map.of());
    }

    public static String paid(String orderId, String userId, long version, String paymentId) {
        return build(OrderDomainEvent.TYPE_PAID, orderId, userId, version,
                paymentId == null ? Map.of() : Map.of("paymentId", paymentId));
    }

    public static String shipped(String orderId, String userId, long version) {
        return build(OrderDomainEvent.TYPE_SHIPPED, orderId, userId, version, Map.of());
    }

    public static String cancelled(String orderId, String userId, long version) {
        return build(OrderDomainEvent.TYPE_CANCELLED, orderId, userId, version, Map.of());
    }

    private static String build(String eventType, String orderId, String userId,
                                long version, Map<String, Object> extras) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schemaVersion", SCHEMA_VERSION);
        body.put("eventId", "evt-" + UUID.randomUUID());
        body.put("eventType", eventType);
        body.put("orderId", orderId);
        body.put("userId", userId);
        body.put("version", version);
        body.put("occurredAt", Instant.now().toString());
        body.putAll(extras);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            // 只有全受控的 String/Map，序列化不该失败；真失败了说明是代码问题，别静默
            throw new IllegalStateException("Failed to build order event payload: " + eventType, e);
        }
    }
}
