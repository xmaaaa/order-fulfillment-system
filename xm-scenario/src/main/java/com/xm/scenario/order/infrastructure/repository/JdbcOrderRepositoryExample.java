package com.xm.scenario.order.infrastructure.repository;

import com.xm.scenario.order.domain.model.Order;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.model.OrderLine;
import com.xm.scenario.order.domain.model.OrderRepository;
import com.xm.scenario.order.domain.state.OrderState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

/**
 * 订单仓储 JDBC 示例：乐观锁 CAS 用「WHERE version = ?」+「SET version = version + 1」。
 * 大厂常见：DB 侧 version+1，避免应用层与 DB 不一致；affectedRows=0 即冲突重试或抛异常。
 *
 * 表结构示例：
 *   t_order(id, user_id, state, version, created_at)
 *   t_order_line(id, order_id, sku_id, quantity, price)
 */
public class JdbcOrderRepositoryExample implements OrderRepository {

    private final JdbcTemplate jdbc;

    public JdbcOrderRepositoryExample(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Order order) {
        jdbc.update(
                "INSERT INTO t_order (id, user_id, state, version) VALUES (?, ?, ?, ?)",
                order.getId().getValue(), order.getUserId(), order.getState().name(), order.getVersion()
        );
        for (OrderLine line : order.getLines()) {
            jdbc.update(
                    "INSERT INTO t_order_line (order_id, sku_id, quantity, price) VALUES (?, ?, ?, ?)",
                    order.getId().getValue(), line.getSkuId(), line.getQuantity(), line.getPrice()
            );
        }
    }

    @Override
    public Order findById(OrderId id) {
        List<Order> list = jdbc.query(
                "SELECT id, user_id, state, version FROM t_order WHERE id = ?",
                orderRowMapper, id.getValue()
        );
        if (list.isEmpty()) return null;
        Order head = list.get(0);
        List<OrderLine> lines = jdbc.query(
                "SELECT sku_id, quantity, price FROM t_order_line WHERE order_id = ?",
                (rs, i) -> new OrderLine(
                        rs.getString("sku_id"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("price")
                ),
                id.getValue()
        );
        return new Order(head.getId(), head.getUserId(), lines, head.getState(), head.getVersion());
    }

    /**
     * 乐观锁 CAS：DB 侧 version = version + 1，仅当 WHERE version = 旧值 时更新；
     * affectedRows = 0 表示已被别人改过，冲突。
     */
    @Override
    public boolean updateVersion(Order order) {
        long expectedVersion = order.getVersion() - 1;  // 聚合内已 +1，库中应是旧值
        int rows = jdbc.update(
                "UPDATE t_order SET state = ?, version = version + 1 WHERE id = ? AND version = ?",
                order.getState().name(), order.getId().getValue(), expectedVersion
        );
        if (rows == 0) return false;
        // 可选：再查一次把最新 version 同步到内存，或由上层重试时重新 load
        return true;
    }

    private static final RowMapper<Order> orderRowMapper = (rs, i) -> {
        OrderId id = new OrderId(rs.getString("id"));
        String userId = rs.getString("user_id");
        OrderState state = OrderState.valueOf(rs.getString("state"));
        long version = rs.getLong("version");
        return new Order(id, userId, List.of(), state, version);
    };
}
