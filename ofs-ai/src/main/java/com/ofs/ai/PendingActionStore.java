package com.ofs.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 待确认操作登记处（human-in-the-loop）。
 * 危险工具不直接执行，只登记动作数据并返回 actionId。
 * 确认时取出并删除，同一 actionId 只能成功取出一次；超过 TTL 视为失效。
 * 仍在内存中：多副本和重启会丢。数据本身已经可序列化，换 Redis 时接口不用改。
 */
@Component
public class PendingActionStore {

    public static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, PendingAction> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1000);
    private final Clock clock;

    public PendingActionStore() {
        this(Clock.systemUTC());
    }

    PendingActionStore(Clock clock) {
        this.clock = clock;
    }

    /** 登记一个待确认操作，返回 actionId。 */
    public String propose(String action, String orderId, String paymentId, String description) {
        String id = "act-" + seq.incrementAndGet();
        store.put(id, new PendingAction(action, orderId, paymentId, description, clock.instant()));
        return id;
    }

    /**
     * 查看但不删除。不存在或已超过 TTL 返回 empty；过期项会从登记处清掉。
     */
    public Optional<PendingAction> peek(String actionId) {
        PendingAction pending = store.get(actionId);
        if (pending == null) {
            return Optional.empty();
        }
        if (isExpired(pending, clock.instant())) {
            store.remove(actionId, pending);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    /**
     * 取出并删除。不存在、已确认过、或已超过 TTL，都返回 empty。
     * 同一 actionId 只有一次能取到。
     */
    public Optional<PendingAction> take(String actionId) {
        PendingAction pending = store.remove(actionId);
        if (pending == null || isExpired(pending, clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    private static boolean isExpired(PendingAction pending, Instant now) {
        return pending.createdAt().plus(TTL).isBefore(now);
    }
}
