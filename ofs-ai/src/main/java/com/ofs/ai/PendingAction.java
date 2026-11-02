package com.ofs.ai;

import java.time.Instant;

/**
 * 待确认的敏感操作。只存数据，不存闭包，之后可以原样放进 Redis。
 * action 取值：payOrder、shipOrder、cancelOrder。paymentId 仅支付时有值。
 */
public record PendingAction(
        String action,
        String orderId,
        String paymentId,
        String description,
        Instant createdAt
) {
}
