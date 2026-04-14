-- 本地消息表 / 事务型 Outbox（与业务表同库，同一事务写入）
-- 大厂常见：扫表发 MQ，下游幂等消费，实现最终一致

CREATE TABLE IF NOT EXISTS outbox_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合根ID，如 orderId',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    payload VARCHAR(4096) NOT NULL COMMENT '消息体 JSON',
    topic VARCHAR(128) NOT NULL COMMENT '目标 topic/queue',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=PENDING 1=SENT 2=FAILED',
    created_at BIGINT NOT NULL COMMENT '创建时间戳 ms',
    sent_at BIGINT NULL COMMENT '发送成功时间戳',
    UNIQUE KEY uk_aggregate_event (aggregate_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 待发消息';

-- 幂等键表（可选，也可用 Redis SET key NX PX）
CREATE TABLE IF NOT EXISTS idempotency_key (
    idempotency_key VARCHAR(256) PRIMARY KEY,
    result_payload VARCHAR(4096) NULL COMMENT '首次结果缓存',
    expire_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等键';
