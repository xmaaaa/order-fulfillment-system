package com.xm.config.sentinel;

import com.xm.scenario.inventory.client.InventoryClient;

/**
 * {@link InventoryClient} wrapped with Sentinel resource {@link #RESOURCE}.
 */
public class SentinelInventoryClient implements InventoryClient {

    public static final String RESOURCE = "inventoryClient";

    private final InventoryClient delegate;

    public SentinelInventoryClient(InventoryClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean reserve(String skuId, int quantity, String warehouseId, String reserveId) {
        return SentinelClientSupport.execute(RESOURCE,
                () -> delegate.reserve(skuId, quantity, warehouseId, reserveId));
    }

    @Override
    public boolean release(String reserveId) {
        return SentinelClientSupport.execute(RESOURCE, () -> delegate.release(reserveId));
    }

    @Override
    public boolean confirmDeduction(String reserveId) {
        return SentinelClientSupport.execute(RESOURCE, () -> delegate.confirmDeduction(reserveId));
    }
}
