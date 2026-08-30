package com.ofs.ai;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 待确认操作登记处（human-in-the-loop 的核心）。
 * 危险工具不直接执行，而是把"真正要做的事"(executor) 连同描述登记进来，返回一个 actionId。
 * 只有当人调用 /confirm?actionId=... 时，才真正执行 executor。
 * 生产环境这里应换成持久化(DB/Redis) + 过期时间；学习先用内存。
 */
@Component
public class PendingActionStore {

    private final Map<String, Pending> store = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger(1000);

    /** 登记一个待确认操作，返回 actionId。executor 是"确认后真正执行"的逻辑。 */
    public String propose(String description, Supplier<String> executor) {
        String id = "act-" + seq.incrementAndGet();
        store.put(id, new Pending(description, executor));
        return id;
    }

    /** 人工确认：执行并移除该操作。返回执行结果；无效 id 则提示。 */
    public String confirm(String actionId) {
        Pending p = store.remove(actionId);
        if (p == null) {
            return "无效或已处理的确认ID：" + actionId;
        }
        return p.executor.get();
    }

    record Pending(String description, Supplier<String> executor) {
    }
}
