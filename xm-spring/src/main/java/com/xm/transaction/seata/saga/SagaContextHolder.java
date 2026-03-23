package com.xm.transaction.seata.saga;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储 Saga 正向执行产生的中间数据，供补偿使用。
 * Seata 状态机上下文不直接支持复杂对象，用此类桥接。
 */
final class SagaContextHolder {

    private static final Map<String, List<String>> RESERVE_IDS = new ConcurrentHashMap<>();

    static void setReserveIds(String orderId, List<String> reserveIds) {
        RESERVE_IDS.put(orderId, reserveIds);
    }

    static List<String> getReserveIds(String orderId) {
        return RESERVE_IDS.remove(orderId);
    }
}
