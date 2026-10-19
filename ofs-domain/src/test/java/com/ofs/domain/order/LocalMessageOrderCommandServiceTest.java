package com.ofs.domain.order;

import com.ofs.domain.order.application.command.LocalMessageOrderCommandService;
import com.ofs.domain.order.application.command.OrderCommandService;
import com.ofs.domain.order.application.command.OrderCommandServiceImpl;
import com.ofs.domain.order.domain.model.OrderId;
import com.ofs.domain.order.domain.model.OrderRepository;
import com.ofs.domain.order.domain.service.OrderDomainService;
import com.ofs.domain.order.infrastructure.repository.InMemoryOrderRepository;
import com.ofs.domain.shared.event.OrderEventPayloads;
import com.ofs.domain.transaction.localmessage.InMemoryLocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport;
import com.ofs.domain.transaction.localmessage.LocalMessageTxSupport.PendingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地消息表装饰器：验证<b>每一次</b>状态流转都写了 outbox，而不是只有 createDraft。
 * 这是接上 outbox 缺口之前的老行为——submit/markPaid/ship/cancel 一个事件都不发。
 */
class LocalMessageOrderCommandServiceTest {

    private InMemoryLocalMessageTxSupport outbox;
    private OrderCommandService service;

    @BeforeEach
    void setUp() {
        OrderRepository repo = new InMemoryOrderRepository();
        outbox = new InMemoryLocalMessageTxSupport();
        service = new LocalMessageOrderCommandService(
                new OrderCommandServiceImpl(new OrderDomainService(repo)), outbox, repo);
    }

    private OrderId newOrder() {
        return service.createDraft("user-1", List.of(
                new OrderCommandService.OrderLineDto("SKU-1", 2, new BigDecimal("50.00"))
        ));
    }

    private List<PendingMessage> pending() {
        return outbox.scanPending(100);
    }

    private Map<String, PendingMessage> byEventType() {
        return pending().stream().collect(Collectors.toMap(
                PendingMessage::eventType, Function.identity()));
    }

    @Test
    void everyTransitionWritesAnOutboxMessage() {
        OrderId id = newOrder();
        service.submit(id);
        service.markPaid(id, "PAY-1");
        service.ship(id);

        List<PendingMessage> messages = pending();
        assertEquals(4, messages.size(),
                "createDraft/submit/markPaid/ship 各应写一条 outbox，实际: " + messages);

        Map<String, PendingMessage> byType = byEventType();
        assertEquals("order.created", byType.get("OrderCreated").topic());
        assertEquals("order.submitted", byType.get("OrderSubmitted").topic());
        assertEquals("order.paid", byType.get("OrderPaid").topic());
        assertEquals("order.shipped", byType.get("OrderShipped").topic());
    }

    @Test
    void cancelWritesOutboxMessage() {
        OrderId id = newOrder();
        service.cancel(id);

        Map<String, PendingMessage> byType = byEventType();
        assertTrue(byType.containsKey("OrderCancelled"));
        assertEquals("order.cancelled", byType.get("OrderCancelled").topic());
    }

    /** aggregateId 是 Kafka 的分区键，必须是 orderId——否则同一订单的消息会散到不同分区、乱序 */
    @Test
    void everyMessageIsKeyedByOrderId() {
        OrderId id = newOrder();
        service.submit(id);
        service.markPaid(id, "PAY-1");

        for (PendingMessage m : pending()) {
            assertEquals(id.getValue(), m.aggregateId(),
                    "eventType=" + m.eventType() + " 的 aggregateId 应为 orderId");
        }
    }

    /** version 用于下游判断「这条是不是迟到的旧消息」，必须是流转<b>之后</b>的值 */
    @Test
    void payloadCarriesPostTransitionVersion() {
        OrderId id = newOrder();
        service.submit(id);
        service.markPaid(id, "PAY-1");

        Map<String, PendingMessage> byType = byEventType();
        // 建单时 version=0；之后每次 transition() 都 +1
        assertTrue(byType.get("OrderCreated").payload().contains("\"version\":0"),
                "实际: " + byType.get("OrderCreated").payload());
        assertTrue(byType.get("OrderSubmitted").payload().contains("\"version\":1"),
                "实际: " + byType.get("OrderSubmitted").payload());
        assertTrue(byType.get("OrderPaid").payload().contains("\"version\":2"),
                "实际: " + byType.get("OrderPaid").payload());
    }

    /** userId 让通知服务不用回查订单就知道通知谁 */
    @Test
    void payloadCarriesUserId() {
        OrderId id = newOrder();
        service.submit(id);

        for (PendingMessage m : pending()) {
            assertTrue(m.payload().contains("\"userId\":\"user-1\""),
                    m.eventType() + " 应带 userId，实际: " + m.payload());
        }
    }

    /** schemaVersion 让将来做破坏性变更时下游能分流，而不是解析失败 */
    @Test
    void payloadCarriesSchemaVersion() {
        OrderId id = newOrder();
        assertTrue(byEventType().get("OrderCreated").payload()
                        .contains("\"schemaVersion\":" + OrderEventPayloads.SCHEMA_VERSION),
                "实际: " + byEventType().get("OrderCreated").payload());
    }

    @Test
    void payloadIsJsonCarryingOrderIdAndEventType() {
        OrderId id = newOrder();

        PendingMessage created = byEventType().get("OrderCreated");
        assertTrue(created.payload().contains("\"orderId\":\"" + id.getValue() + "\""),
                "payload 应含 orderId: " + created.payload());
        assertTrue(created.payload().contains("\"eventType\":\"OrderCreated\""));
        assertTrue(created.payload().contains("\"occurredAt\""));
    }

    /** markPaid 的业务字段要进 payload，否则下游拿不到支付单号 */
    @Test
    void paidPayloadCarriesPaymentId() {
        OrderId id = newOrder();
        service.submit(id);
        service.markPaid(id, "PAY-XYZ");

        String payload = byEventType().get("OrderPaid").payload();
        assertTrue(payload.contains("\"paymentId\":\"PAY-XYZ\""), "实际: " + payload);
    }

    /** 业务失败时不该留下事件——否则下游会收到一个从没发生过的状态流转 */
    @Test
    void failedTransitionWritesNoOutboxMessage() {
        OrderId id = newOrder();
        int before = pending().size();

        // DRAFT 不能直接 PAY
        assertThrows(RuntimeException.class, () -> service.markPaid(id, "PAY-1"));

        assertEquals(before, pending().size(), "失败的流转不该写 outbox");
    }

    /** 同一订单的多条事件用 orderId 当 key，能被 relay 一次扫出来 */
    @Test
    void messagesAreDrainedByRelayScan() {
        OrderId id = newOrder();
        service.submit(id);

        List<PendingMessage> first = outbox.scanPending(100);
        assertEquals(2, first.size());
        first.forEach(m -> outbox.markSent(m.id()));
        assertTrue(outbox.scanPending(100).isEmpty(), "markSent 之后不应再扫到");
    }

    /** OutboxRecord 的必填校验：漏了 aggregateId 就等于漏了分区键 */
    @Test
    void outboxRecordRejectsMissingMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalMessageTxSupport.OutboxRecord("", "OrderPaid", "{}", "order.paid"));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalMessageTxSupport.OutboxRecord("ORD-1", null, "{}", "order.paid"));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalMessageTxSupport.OutboxRecord("ORD-1", "OrderPaid", "{}", " "));
    }
}
