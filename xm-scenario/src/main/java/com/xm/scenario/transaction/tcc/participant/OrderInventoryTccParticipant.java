package com.xm.scenario.transaction.tcc.participant;

import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.service.OrderDomainService;
import com.xm.scenario.transaction.tcc.TccCoordinator;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习用：订单维度库存 TCC 参与者，按订单行预占/确认/释放。
 */
public class OrderInventoryTccParticipant implements TccCoordinator.TccParticipant {

    private final InventoryClient inventoryClient;
    private final OrderDomainService orderDomainService;
    private static final String KEY_RESERVE_IDS = "reserveIds";

    public OrderInventoryTccParticipant(InventoryClient inventoryClient, OrderDomainService orderDomainService) {
        this.inventoryClient = inventoryClient;
        this.orderDomainService = orderDomainService;
    }

    @Override
    public boolean tryPhase(TccCoordinator.TccContext context) {
        var order = orderDomainService.findOrder(new OrderId(context.getOrderId())).orElse(null);
        if (order == null) return false;
        List<String> ids = new ArrayList<>();
        for (var line : order.getLines()) {
            String reserveId = context.getOrderId() + ":inventory:" + line.getSkuId();
            if (!inventoryClient.reserve(line.getSkuId(), line.getQuantity(), "WH01", reserveId)) {
                ids.forEach(inventoryClient::release);
                return false;
            }
            ids.add(reserveId);
        }
        if (context instanceof com.xm.scenario.transaction.tcc.MutableTccContext mutable) {
            mutable.setReserveId(String.join(",", ids));
        }
        return true;
    }

    @Override
    public boolean confirm(TccCoordinator.TccContext context) {
        String ids = context.getReserveId();
        if (ids == null || ids.isBlank()) return true;
        for (String id : ids.split(",")) {
            if (!inventoryClient.confirmDeduction(id.trim())) return false;
        }
        return true;
    }

    @Override
    public boolean cancel(TccCoordinator.TccContext context) {
        String ids = context.getReserveId();
        if (ids == null || ids.isBlank()) return true;
        for (String id : ids.split(",")) {
            inventoryClient.release(id.trim());
        }
        return true;
    }
}
