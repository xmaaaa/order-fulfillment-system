package com.xm.scenario.inventory.client;

/**
 * 库存上下文防腐层：本模块只依赖此接口。
 */
public interface InventoryClient {

    /**
     * 预占/扣减库存（Try 或 直接扣减，由实现决定）
     *
     * @param skuId     商品 SKU
     * @param quantity  数量
     * @param warehouseId 仓库 ID（可选）
     * @param reserveId 预占单号（用于取消预占）
     * @return 是否成功
     */
    boolean reserve(String skuId, int quantity, String warehouseId, String reserveId);

    /**
     * 释放预占（Cancel 补偿）
     *
     * @param reserveId 预占单号
     * @return 是否成功
     */
    boolean release(String reserveId);

    /**
     * 确认扣减（Confirm 阶段，若采用 TCC）
     *
     * @param reserveId 预占单号
     * @return 是否成功
     */
    boolean confirmDeduction(String reserveId);
}
