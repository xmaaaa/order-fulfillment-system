package com.xm.scenario.contract;

import com.xm.scenario.inventory.client.StubInventoryClient;
import com.xm.scenario.payment.client.StubPaymentClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 契约测试：桩实现必须满足防腐层接口的约定，便于日后替换为 Feign 时行为一致。
 */
class PaymentInventoryClientContractTest {

    @Test
    void paymentCreatePaymentReturnsNonBlankId() {
        var c = new StubPaymentClient();
        String id = c.createPayment("ORD-1", new BigDecimal("10.00"), "user1");
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    void paymentCancelOrRefundIdempotentOnUnknownId() {
        var c = new StubPaymentClient();
        assertDoesNotThrow(() -> c.cancelOrRefund("non-existent-payment-id"));
    }

    @Test
    void inventoryReserveThenRelease() {
        var c = new StubInventoryClient();
        String rid = "res-1";
        assertTrue(c.reserve("SKU-1", 1, "WH1", rid));
        assertTrue(c.release(rid));
    }

    @Test
    void inventoryConfirmDeductionClearsReserve() {
        var c = new StubInventoryClient();
        String rid = "rid-3";
        assertTrue(c.reserve("SKU-1", 2, "WH1", rid));
        assertEquals(2, c.getReservedQuantity(rid));
        assertTrue(c.confirmDeduction(rid));
        assertEquals(0, c.getReservedQuantity(rid));
    }
}
