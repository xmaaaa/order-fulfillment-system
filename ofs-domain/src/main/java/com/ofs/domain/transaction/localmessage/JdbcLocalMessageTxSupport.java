package com.ofs.domain.transaction.localmessage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 本地消息表 JDBC 实现：业务与 outbox 同事务，需 outbox_message 表（见 {@code db/outbox_schema.sql}）。
 *
 * <p>写入的 aggregate_id / event_type 取自 {@link OutboxRecord}，不能写死——表上有
 * {@code UNIQUE KEY uk_aggregate_event (aggregate_id, event_type)}，写死意味着整张表只能存下一行，
 * 第二条消息就会撞唯一键。填真实值之后这个索引才回到它本来的用途：
 * <b>同一订单的同一事件只入队一次</b>。
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
    public void executeInLocalTx(Runnable businessAction, Supplier<OutboxRecord> recordSupplier) {
        transactionTemplate.executeWithoutResult(status -> {
            businessAction.run();
            OutboxRecord record = recordSupplier.get();
            if (record != null) {
                insertMessage(record);
            }
        });
    }

    private void insertMessage(OutboxRecord record) {
        jdbc.update(
                "INSERT INTO outbox_message (aggregate_id, event_type, payload, topic, status, created_at) VALUES (?,?,?,?,?,?)",
                record.aggregateId(), record.eventType(), record.payload(), record.topic(),
                STATUS_PENDING, System.currentTimeMillis()
        );
    }

    @Override
    public void markSent(long messageId) {
        jdbc.update("UPDATE outbox_message SET status = ?, sent_at = ? WHERE id = ?",
                STATUS_SENT, System.currentTimeMillis(), messageId);
    }

    @Override
    public List<PendingMessage> scanPending(int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, aggregate_id, event_type, payload, topic, created_at "
                        + "FROM outbox_message WHERE status = ? ORDER BY id LIMIT ?",
                STATUS_PENDING, limit
        );
        return rows.stream()
                .map(r -> new PendingMessage(
                        ((Number) r.get("id")).longValue(),
                        (String) r.get("aggregate_id"),
                        (String) r.get("event_type"),
                        (String) r.get("payload"),
                        (String) r.get("topic"),
                        ((Number) r.get("created_at")).longValue()))
                .toList();
    }
}
