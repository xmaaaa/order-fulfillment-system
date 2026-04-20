package com.xm.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.xm.config.sentinel.SentinelInventoryClient;
import com.xm.config.sentinel.SentinelPaymentClient;
import com.xm.scenario.inventory.client.InventoryClient;
import com.xm.scenario.payment.client.PaymentClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 熔断/降级：装饰 {@link PaymentClient}、{@link InventoryClient}。
 * 异常比例 50%、最小请求数 5、熔断恢复窗口 30s。
 * <p>
 * 生效条件：{@code xm.scenario.circuit-breaker=true}（沿用原配置键）。
 * Dashboard：{@code spring.cloud.sentinel.transport.dashboard}
 */
@Configuration
@ConditionalOnProperty(name = "xm.scenario.circuit-breaker", havingValue = "true")
public class SentinelClientConfig {

    @PostConstruct
    public void registerDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();
        rules.add(buildExceptionRatioRule(SentinelPaymentClient.RESOURCE));
        rules.add(buildExceptionRatioRule(SentinelInventoryClient.RESOURCE));
        DegradeRuleManager.loadRules(rules);
    }

    private static DegradeRule buildExceptionRatioRule(String resource) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(0.5);
        rule.setTimeWindow(30);
        rule.setMinRequestAmount(5);
        rule.setStatIntervalMs(10_000);
        return rule;
    }

    @Bean("scenarioPaymentClient")
    public PaymentClient scenarioPaymentClient(
            @Qualifier("scenarioPaymentClientStub") PaymentClient delegate) {
        return new SentinelPaymentClient(delegate);
    }

    @Bean("scenarioInventoryClient")
    public InventoryClient scenarioInventoryClient(
            @Qualifier("scenarioInventoryClientStub") InventoryClient delegate) {
        return new SentinelInventoryClient(delegate);
    }
}
