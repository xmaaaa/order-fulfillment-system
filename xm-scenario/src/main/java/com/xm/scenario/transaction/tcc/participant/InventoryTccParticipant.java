package com.xm.scenario.transaction.tcc.participant;

import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.transaction.tcc.MutableTccContext;
import com.xm.scenario.transaction.tcc.TccCoordinator;

/**
 * 学习用：单 SKU 库存 TCC 参与者。Try=预占，Confirm=确认扣减，Cancel=释放。
 */
public class InventoryTccParticipant implements TccCoordinator.TccParticipant {

    private final InventoryClient inventoryClient;
    private final String skuId;
    private final int quantity;
    private final String warehouseId;

    public InventoryTccParticipant(InventoryClient inventoryClient, String skuId, int quantity, String warehouseId) {
        this.inventoryClient = inventoryClient;
        this.skuId = skuId;
        this.quantity = quantity;
        this.warehouseId = warehouseId;
    }

    @Override
    public boolean tryPhase(TccCoordinator.TccContext context) {
        String reserveId = context.getOrderId() + ":inventory:" + skuId;
        boolean ok = inventoryClient.reserve(skuId, quantity, warehouseId, reserveId);
        if (ok && context instanceof MutableTccContext mutable) {
            mutable.setReserveId(reserveId);
        }
        return ok;
    }

    @Override
    public boolean confirm(TccCoordinator.TccContext context) {
        return inventoryClient.confirmDeduction(context.getReserveId());
    }

    @Override
    public boolean cancel(TccCoordinator.TccContext context) {
        return inventoryClient.release(context.getReserveId());
    }
}
