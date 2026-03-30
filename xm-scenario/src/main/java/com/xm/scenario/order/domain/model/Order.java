package com.xm.scenario.order.domain.model;

import com.xm.scenario.order.domain.state.OrderEvent;
import com.xm.scenario.order.domain.state.OrderState;
import com.xm.scenario.order.domain.state.OrderStateMachine;
import com.xm.scenario.order.domain.state.TransitionGuard;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 订单聚合根。
 * 职责：维护订单头+订单行一致性；通过状态机控制状态流转。
 *
 * @author eddiema
 */
public class Order {

    private final OrderId id;
    private final String userId;
    private final List<OrderLine> lines;
    private OrderState state;
    // 乐观锁版本（可选，持久化时使用）
    private long version;
    /** 提交时间戳（ms），用于超时自动取消；仅 SUBMITTED 时有效 */
    private long submittedAtEpochMs;

    public Order(OrderId id, String userId, List<OrderLine> lines) {
        if (id == null || userId == null || userId.isBlank() || lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Order requires id, userId and non-empty lines");
        }
        this.id = id;
        this.userId = userId;
        this.lines = new ArrayList<>(lines);
        this.state = OrderState.DRAFT;
        this.version = 0L;
        this.submittedAtEpochMs = 0L;
    }

    /** 用于从持久化还原 */
    public Order(OrderId id, String userId, List<OrderLine> lines, OrderState state, long version) {
        this(id, userId, lines, state, version, 0L);
    }

    /** 用于从持久化还原（含 submittedAt） */
    public Order(OrderId id, String userId, List<OrderLine> lines, OrderState state, long version, long submittedAtEpochMs) {
        this.id = id;
        this.userId = userId;
        this.lines = new ArrayList<>(lines);
        this.state = state;
        this.version = version;
        this.submittedAtEpochMs = submittedAtEpochMs;
    }

    public OrderId getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public OrderState getState() {
        return state;
    }

    public long getVersion() {
        return version;
    }

    public long getSubmittedAtEpochMs() {
        return submittedAtEpochMs;
    }

    public BigDecimal getTotalAmount() {
        return lines.stream().map(OrderLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 状态机流转：仅当当前状态允许该事件且守卫通过时才变更状态。
     *
     * @param event 领域事件
     * @return 是否成功流转
     */
    public boolean transition(OrderEvent event) {
        return transition(event, TransitionGuard.none());
    }

    /**
     * 带守卫的流转：守卫不通过则不允许流转（大厂常见：金额>0、地址已填等）。
     * 流转时在聚合内做 version+1，供持久层 CAS（WHERE version = ? 且 SET version = version + 1）。
     */
    public boolean transition(OrderEvent event, TransitionGuard guard) {
        OrderState next = OrderStateMachine.next(state, event);
        if (next == null || !guard.allow(state, event, this)) {
            return false;
        }
        this.state = next;
        this.version++;  // 乐观锁：先 +1，持久层用「WHERE version = 旧值」做 CAS
        if (next == OrderState.SUBMITTED) {
            this.submittedAtEpochMs = System.currentTimeMillis();
        }
        return true;
    }

    /**
     * 领域校验：仅草稿/已提交可取消等，可在聚合内再细化。
     */
    public boolean canCancel() {
        return OrderStateMachine.canTransition(state, OrderEvent.CANCEL);
    }
}
