package com.xm.scenario.transaction.localmessage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * 框架用：本地消息表 JDBC 实现，与业务表同库同事务。需 outbox_message 表。
 */
public class JdbcLocalMessageTxSupport implements LocalMessageTxSupport {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SENT = 1;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public JdbcLocalMessageTxSupport(DataSource dataSource, TransactionTemplate transactionTemplate) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void executeInLocalTx(Runnable businessAction, Object message, String topic) {
        transactionTemplate.executeWithoutResult(status -> {
            businessAction.run();
            String payload = message == null ? "" : message.toString();
            String aggregateId = "order";
            String eventType = "OrderEvent";
            jdbc.update(
                    "INSERT INTO outbox_message (aggregate_id, event_type, payload, topic, status, created_at) VALUES (?,?,?,?,?,?)",
                    aggregateId, eventType, payload, topic, STATUS_PENDING, System.currentTimeMillis()
            );
        });
    }

    /** 框架用：action 与 写消息 在同一 DB 事务内，保证原子性 */
    @Override
    public <T> T executeInLocalTxWithResult(Callable<T> action, Function<T, Object> messageBuilder, String topic) {
        return transactionTemplate.execute(status -> {
            try {
                T result = action.call();
                Object msg = messageBuilder.apply(result);
                String payload = msg == null ? "" : msg.toString();
                jdbc.update(
                        "INSERT INTO outbox_message (aggregate_id, event_type, payload, topic, status, created_at) VALUES (?,?,?,?,?,?)",
                        "order", "OrderEvent", payload, topic, STATUS_PENDING, System.currentTimeMillis()
                );
                return result;
            } catch (Exception e) {
                if (e instanceof RuntimeException re) throw re;
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void markSent(long messageId) {
        jdbc.update("UPDATE outbox_message SET status = ?, sent_at = ? WHERE id = ?",
                STATUS_SENT, System.currentTimeMillis(), messageId);
    }

    @Override
    public List<PendingMessage> scanPending(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, payload, topic, created_at FROM outbox_message WHERE status = ? ORDER BY id LIMIT ?",
                STATUS_PENDING, limit
        );
        return rows.stream()
                .map(r -> new PendingMessage(
                        ((Number) r.get("id")).longValue(),
                        (String) r.get("payload"),
                        (String) r.get("topic"),
                        ((Number) r.get("created_at")).longValue()))
                .toList();
    }
}
