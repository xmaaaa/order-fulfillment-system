package com.ofs.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 暴露给大模型的订单工具 —— M2.5：真正通过 HTTP 调用订单系统（ofs-app, 默认 :8888）。
 * base-url 走配置：本地是 localhost:8888，将来上 K8s 改成 Service DNS 名即可，代码不用动。
 */
@Component
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    private static final Map<String, Set<String>> ALLOWED_STATES = Map.of(
            "payOrder", Set.of("SUBMITTED"),
            "shipOrder", Set.of("PAID"),
            "cancelOrder", Set.of("DRAFT", "SUBMITTED", "PAID")
    );

    private final RestClient restClient;
    private final PendingActionStore pendingStore;

    public OrderTools(@Value("${order-system.base-url:http://localhost:8888}") String baseUrl,
                      PendingActionStore pendingStore) {
        this(RestClient.builder().baseUrl(baseUrl), pendingStore);
        log.info("OrderTools 初始化，订单系统地址 = {}", baseUrl);
    }

    /** 测试注入可拦截的 RestClient。 */
    OrderTools(RestClient.Builder restClientBuilder, PendingActionStore pendingStore) {
        this.restClient = restClientBuilder.build();
        this.pendingStore = pendingStore;
    }

    @Tool(description = "根据订单号查询订单的当前状态、所属用户和总金额")
    public Map<String, Object> queryOrder(
            @ToolParam(description = "订单号") String orderId) {

        log.info("🔧 [工具] queryOrder(orderId={}) → GET /order/{}", orderId, orderId);
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/order/{orderId}", orderId)
                    .retrieve()
                    .body(Map.class);
            return body;
        } catch (Exception e) {
            log.warn("查询订单失败: {}", e.getMessage());
            return Map.of("orderId", orderId, "error", "查询失败或订单不存在：" + e.getMessage());
        }
    }

    @Tool(description = "为指定用户创建一张草稿订单，需要商品SKU、数量和单价，返回新订单号")
    public Map<String, Object> createDraftOrder(
            @ToolParam(description = "用户ID，例如 user1") String userId,
            @ToolParam(description = "商品SKU编号，例如 sku1") String skuId,
            @ToolParam(description = "购买数量") int quantity,
            @ToolParam(description = "商品单价") double price) {

        log.info("🔧 [工具] createDraftOrder(userId={}, skuId={}, qty={}, price={})",
                userId, skuId, quantity, price);
        try {
            Map<String, Object> requestBody = Map.of(
                    "userId", userId,
                    "lines", List.of(Map.of("skuId", skuId, "quantity", quantity, "price", price))
            );
            Map<String, Object> body = restClient.post()
                    .uri("/order/draft")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            return body;
        } catch (Exception e) {
            log.warn("创建订单失败: {}", e.getMessage());
            return Map.of("error", "创建订单失败：" + e.getMessage());
        }
    }

    // ── 安全操作：直接执行 ──────────────────────────────────────────────
    @Tool(description = "提交订单：把草稿(DRAFT)订单提交为待支付(SUBMITTED)")
    public String submitOrder(@ToolParam(description = "订单号") String orderId) {
        return doActionNow("submitOrder", orderId, "/order/{orderId}/submit");
    }

    // ── 敏感操作：不直接执行，登记为"待人工确认" ─────────────────────────
    @Tool(description = "支付订单（敏感操作，需用户确认后才会真正执行）：把待支付订单标记为已支付")
    public String payOrder(
            @ToolParam(description = "订单号") String orderId,
            @ToolParam(description = "支付流水号，可自拟一个，例如 pay-001") String paymentId) {
        return proposeGated("支付订单 " + orderId + "（流水号 " + paymentId + "）",
                "payOrder", orderId, paymentId);
    }

    @Tool(description = "发货订单（敏感操作，需用户确认后才会真正执行）：把已支付订单标记为已发货")
    public String shipOrder(@ToolParam(description = "订单号") String orderId) {
        return proposeGated("发货订单 " + orderId, "shipOrder", orderId, null);
    }

    @Tool(description = "取消订单（敏感操作，需用户确认后才会真正执行）：在发货前取消订单")
    public String cancelOrder(@ToolParam(description = "订单号") String orderId) {
        return proposeGated("取消订单 " + orderId, "cancelOrder", orderId, null);
    }

    /**
     * 人工确认入口。取出登记的动作后，先查订单当前状态，符合才真正 POST。
     */
    public String confirm(String actionId) {
        PendingAction pending = pendingStore.peek(actionId).orElse(null);
        if (pending == null) {
            return "无效、已过期或已处理的确认ID：" + actionId;
        }
        String state = currentState(pending.orderId());
        if (state == null) {
            return "拒绝执行：无法确认订单 " + pending.orderId() + " 的当前状态，操作未执行。";
        }
        Set<String> allowed = ALLOWED_STATES.getOrDefault(pending.action(), Set.of());
        if (!allowed.contains(state)) {
            pendingStore.take(actionId);
            return "拒绝执行：订单 " + pending.orderId() + " 当前状态为 " + state
                    + "，" + actionLabel(pending.action()) + "要求 " + requiredStates(pending.action())
                    + "。操作未执行。";
        }
        if (pendingStore.take(actionId).isEmpty()) {
            return "无效、已过期或已处理的确认ID：" + actionId;
        }
        return doActionNow(pending.action(), pending.orderId(), uriFor(pending));
    }

    /** 敏感操作：只登记动作数据，不真正执行。返回给模型一段"需确认"的说明。 */
    private String proposeGated(String description, String action, String orderId, String paymentId) {
        log.info("🔒 [待确认] {}", description);
        String actionId = pendingStore.propose(action, orderId, paymentId, description);
        return "⚠️ 需要人工确认：即将【" + description + "】。这是敏感操作，尚未执行。"
                + "已生成待确认项 actionId=" + actionId
                + "，请在 " + PendingActionStore.TTL.toMinutes() + " 分钟内调用 POST /confirm?actionId=" + actionId
                + " 才会真正执行。确认时会再次核对订单状态。";
    }

    private static String uriFor(PendingAction pending) {
        return switch (pending.action()) {
            case "payOrder" -> "/order/{orderId}/paid?paymentId=" + pending.paymentId();
            case "shipOrder" -> "/order/{orderId}/ship";
            case "cancelOrder" -> "/order/{orderId}/cancel";
            default -> throw new IllegalArgumentException("未知动作：" + pending.action());
        };
    }

    private static String actionLabel(String action) {
        return switch (action) {
            case "payOrder" -> "支付";
            case "shipOrder" -> "发货";
            case "cancelOrder" -> "取消";
            default -> action;
        };
    }

    private static String requiredStates(String action) {
        return switch (action) {
            case "payOrder" -> "SUBMITTED";
            case "shipOrder" -> "PAID";
            case "cancelOrder" -> "DRAFT、SUBMITTED 或 PAID";
            default -> "";
        };
    }

    /** 确认前读当前状态。查不到或请求失败返回 null，调用方必须拒绝执行。 */
    private String currentState(String orderId) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/order/{orderId}", orderId)
                    .retrieve()
                    .body(Map.class);
            if (body == null || body.get("state") == null) {
                return null;
            }
            return String.valueOf(body.get("state"));
        } catch (Exception e) {
            log.warn("确认前查询订单失败: {}", e.getMessage());
            return null;
        }
    }

    /** 真正的 HTTP POST（安全操作直接调用；敏感操作在 /confirm 后由 executor 调用）。 */
    private String doActionNow(String action, String orderId, String uri) {
        log.info("🔧 [工具] {}(orderId={}) → POST {}", action, orderId, uri);
        try {
            restClient.post().uri(uri, orderId).retrieve().toBodilessEntity();
            return action + " 成功：订单 " + orderId;
        } catch (Exception e) {
            log.warn("{} 失败: {}", action, e.getMessage());
            return action + " 失败：" + e.getMessage() + "（可能是订单状态不满足该操作）";
        }
    }
}
