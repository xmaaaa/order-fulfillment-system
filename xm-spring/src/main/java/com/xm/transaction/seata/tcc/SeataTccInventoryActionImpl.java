package com.xm.transaction.seata.tcc;

import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.order.domain.model.OrderId;
import com.xm.scenario.order.domain.service.OrderDomainService;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Seata TCC 库存参与者实现。
 *
 * @author eddiema
 */
@Service
@ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "seata")
public class SeataTccInventoryActionImpl implements SeataTccInventoryAction {

    private static final String KEY_RESERVE_IDS = "reserveIds";

    private final InventoryClient inventoryClient;
    private final OrderDomainService orderDomainService;

    public SeataTccInventoryActionImpl(
            @Qualifier("scenarioInventoryClient") InventoryClient inventoryClient,
            OrderDomainService orderDomainService) {
        this.inventoryClient = inventoryClient;
        this.orderDomainService = orderDomainService;
    }

    @Override
    public boolean prepare(BusinessActionContext context, String orderId) {
        var order = orderDomainService.findOrder(new OrderId(orderId)).orElse(null);
        if (order == null) return false;
        List<String> ids = new ArrayList<>();
        for (var line : order.getLines()) {
            String reserveId = orderId + ":seata:inventory:" + line.getSkuId();
            if (!inventoryClient.reserve(line.getSkuId(), line.getQuantity(), "WH01", reserveId)) {
                ids.forEach(inventoryClient::release);
                return false;
            }
            ids.add(reserveId);
        }
        context.getActionContext().put(KEY_RESERVE_IDS, String.join(",", ids));
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext context) {
        String ids = (String) context.getActionContext(KEY_RESERVE_IDS);
        if (ids == null || ids.isBlank()) return true;
        for (String id : ids.split(",")) {
            if (!inventoryClient.confirmDeduction(id.trim())) return false;
        }
        return true;
    }

    @Override
    public boolean rollback(BusinessActionContext context) {
        String ids = (String) context.getActionContext(KEY_RESERVE_IDS);
        if (ids == null || ids.isBlank()) return true;
        for (String id : ids.split(",")) {
            inventoryClient.release(id.trim());
        }
        return true;
    }
}
