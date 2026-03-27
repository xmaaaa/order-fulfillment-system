package com.xm.scenario.order.domain.model;

import java.util.List;

/**
 * 订单仓储（接口在领域层，实现在 infrastructure）。
 * 乐观锁约定：load 后聚合内 transition() 会 version++，updateVersion(order) 时持久层必须做 CAS：
 * 仅当「当前库中 version = order.getVersion() - 1」时才更新，并写入 order（version 已为 +1）。
 * JDBC 写法示例：UPDATE t_order SET state=?, version=version+1, ... WHERE id=? AND version=?；若 affectedRows==0 则冲突。
 */
public interface OrderRepository {

    void save(Order order);

    Order findById(OrderId id);

    /** 乐观锁 CAS：仅当存储中 version == order.version-1 时写入 order（order.version 已在聚合内 +1） */
    boolean updateVersion(Order order);

    /**
     * 查询超时未支付的订单 ID（SUBMITTED 且 submittedAt 早于指定时间戳）。
     * 用于定时任务触发自动取消。
     */
    List<OrderId> findSubmittedOrderIdsOlderThan(long submittedBeforeEpochMs);
}
