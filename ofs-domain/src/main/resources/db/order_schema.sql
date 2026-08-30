-- 订单表（JdbcOrderRepositoryExample 使用）
-- submitted_at：提交时间戳(ms)，用于超时自动取消

CREATE TABLE IF NOT EXISTS t_order (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    submitted_at BIGINT NOT NULL DEFAULT 0 COMMENT '提交时间戳 ms，SUBMITTED 时设置'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS t_order_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(19,4) NOT NULL,
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单行';
