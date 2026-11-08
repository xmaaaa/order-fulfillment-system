package com.ofs.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrderToolsConfirmTest {

    private MockRestServiceServer server;
    private PendingActionStore store;
    private OrderTools tools;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://order.test");
        server = MockRestServiceServer.bindTo(builder).build();
        store = new PendingActionStore();
        tools = new OrderTools(builder, store);
    }

    @Test
    void payOnlyRegistersUntilConfirmed() {
        String reply = tools.payOrder("ORD-1", "pay-001");

        assertThat(reply).contains("尚未执行").contains("actionId=act-");
        server.verify();
    }

    @Test
    void refusesPayWhenOrderIsNoLongerSubmitted() {
        String actionId = store.propose("payOrder", "ORD-1", "pay-001", "支付");
        server.expect(requestTo("http://order.test/order/ORD-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"CANCELLED\"}", MediaType.APPLICATION_JSON));

        String result = tools.confirm(actionId);

        assertThat(result).contains("拒绝执行").contains("CANCELLED").contains("SUBMITTED");
        server.verify();
        assertThat(tools.confirm(actionId)).contains("无效");
    }

    @Test
    void paysWhenOrderIsStillSubmitted() {
        String actionId = store.propose("payOrder", "ORD-1", "pay-001", "支付");
        server.expect(requestTo("http://order.test/order/ORD-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"SUBMITTED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://order.test/order/ORD-1/paid?paymentId=pay-001"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertThat(tools.confirm(actionId)).isEqualTo("payOrder 成功：订单 ORD-1");
        server.verify();
        assertThat(tools.confirm(actionId)).contains("无效");
    }

    @Test
    void keepsTheActionWhenStatusLookupFails() {
        String actionId = store.propose("shipOrder", "ORD-2", null, "发货");
        server.expect(requestTo("http://order.test/order/ORD-2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThat(tools.confirm(actionId)).contains("无法确认订单 ORD-2");
        server.verify();

        server.reset();
        server.expect(requestTo("http://order.test/order/ORD-2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"state\":\"PAID\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://order.test/order/ORD-2/ship"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertThat(tools.confirm(actionId)).isEqualTo("shipOrder 成功：订单 ORD-2");
        server.verify();
    }
}
