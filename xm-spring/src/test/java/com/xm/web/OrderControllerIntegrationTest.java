package com.xm.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = XmBootStarter.class, properties = {
        "management.otlp.tracing.endpoint=",
        "xm.scenario.outbox-relay.enabled=false"
})
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    static {
        System.setProperty("csp.sentinel.log.dir", "target/sentinel");
    }

    private static final String DRAFT_REQUEST = """
            {
              "userId": "user1",
              "lines": [
                {"skuId": "SKU-001", "quantity": 2, "price": 99.00}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void orderLifecycleCanBeDrivenThroughRestApi() throws Exception {
        String orderId = createDraft();

        mockMvc.perform(post("/order/{orderId}/submit", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("SUBMITTED")))
                .andExpect(jsonPath("$.totalAmount", is(198.0)));

        mockMvc.perform(post("/order/{orderId}/paid", orderId)
                        .param("paymentId", "PAY-001"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/order/{orderId}/ship", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", is(orderId)))
                .andExpect(jsonPath("$.userId", is("user1")))
                .andExpect(jsonPath("$.state", is("SHIPPED")))
                .andExpect(jsonPath("$.totalAmount", is(198.0)));
    }

    @Test
    void invalidStateTransitionReturnsConflict() throws Exception {
        String orderId = createDraft();

        mockMvc.perform(post("/order/{orderId}/ship", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ORDER_STATE_CONFLICT")))
                .andExpect(jsonPath("$.path", is("/order/" + orderId + "/ship")));

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));
    }

    @Test
    void missingOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/order/{orderId}", "ORD-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ORDER_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Order not found: ORD-missing")))
                .andExpect(jsonPath("$.path", is("/order/ORD-missing")));
    }

    @Test
    void invalidCreateDraftRequestReturnsBadRequest() throws Exception {
        String invalidRequest = """
                {
                  "userId": "",
                  "lines": []
                }
                """;

        mockMvc.perform(post("/order/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", is("userId and lines required")))
                .andExpect(jsonPath("$.path", is("/order/draft")));
    }

    @Test
    void createDraftHonorsIdempotencyKey() throws Exception {
        String key = "it-create-draft-001";

        String firstOrderId = createDraft(key);
        String secondOrderId = createDraft(key);

        mockMvc.perform(get("/order/{orderId}", firstOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));
        mockMvc.perform(get("/order/{orderId}", secondOrderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DRAFT")));
        org.junit.jupiter.api.Assertions.assertEquals(firstOrderId, secondOrderId);
    }

    @Test
    void tccSubmitWithPaymentMarksSubmittedOrderPaid() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{orderId}/submit", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/order/{orderId}/submit-with-payment-tcc", orderId)
                        .param("amount", "198")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PAID")));
    }

    @Test
    void sagaSubmitWithPaymentMarksSubmittedOrderPaid() throws Exception {
        String orderId = createDraft();
        mockMvc.perform(post("/order/{orderId}/submit", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/order/{orderId}/submit-with-payment-saga", orderId)
                        .param("amount", "198")
                        .param("userId", "user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        mockMvc.perform(get("/order/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("PAID")));
    }

    private String createDraft() throws Exception {
        return createDraft(null);
    }

    private String createDraft(String idempotencyKey) throws Exception {
        var request = post("/order/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content(DRAFT_REQUEST);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("^.*\\\"orderId\\\":\\\"([^\\\"]+)\\\".*$", "$1");
    }
}
