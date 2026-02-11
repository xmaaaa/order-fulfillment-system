package com.xm.scenario.inventory.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 库存防腐层桩实现（学习/单测用）。生产可调用独立库存服务。
 */
public class StubInventoryClient implements InventoryClient {

    private final Map<String, Integer> reserved = new ConcurrentHashMap<>();

    @Override
    public boolean reserve(String skuId, int quantity, String warehouseId, String reserveId) {
        reserved.put(reserveId, quantity);
        return true;
    }

    @Override
    public boolean release(String reserveId) {
        reserved.remove(reserveId);
        return true;
    }

    @Override
    public boolean confirmDeduction(String reserveId) {
        reserved.remove(reserveId);
        return true;
    }

    /** 桩方法：查询当前预占数量（单测用） */
    public int getReservedQuantity(String reserveId) {
        return reserved.getOrDefault(reserveId, 0);
    }
}
