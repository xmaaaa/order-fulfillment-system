package com.xm.config;

import com.xm.scenario.inventory.client.StubInventoryClient;
import com.xm.scenario.payment.client.StubPaymentClient;
import com.xm.scenario.transaction.OrderSubmitWithPaymentSagaService;
import com.xm.scenario.transaction.OrderSubmitWithPaymentTccService;
import com.xm.scenario.transaction.saga.SimpleSagaOrchestrator;
import com.xm.scenario.transaction.saga.SagaOrchestrator;
import com.xm.scenario.transaction.tcc.SimpleTccCoordinator;
import com.xm.scenario.transaction.tcc.TccCoordinator;
import com.xm.transaction.seata.tcc.SeataTccCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 事务场景装配：TCC/Saga 学习用与框架用切换，以及 submit-with-payment 业务用例。
 */
@Configuration
public class TransactionScenarioConfig {

    // ---------- TCC：学习用 ----------
    @Bean
    @ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "learning")
    public TccCoordinator learningTccCoordinator() {
        return new SimpleTccCoordinator();
    }

    // ---------- TCC：框架用（Seata，需 Seata 依赖） ----------
    @Bean
    @ConditionalOnProperty(name = "xm.scenario.tcc", havingValue = "seata")
    @ConditionalOnClass(name = "org.apache.seata.tm.api.GlobalTransactionContext")
    public TccCoordinator seataTccCoordinator() {
        return new SeataTccCoordinator();
    }

    // ---------- Saga：学习用 ----------
    @Bean
    @ConditionalOnProperty(name = "xm.scenario.saga", havingValue = "learning")
    public SagaOrchestrator learningSagaOrchestrator() {
        return new SimpleSagaOrchestrator();
    }

    // ---------- 业务用例：TCC 版 submit-with-payment ----------
    @Bean
    @ConditionalOnBean(TccCoordinator.class)
    public OrderSubmitWithPaymentTccService orderSubmitWithPaymentTccService(
            com.xm.scenario.order.domain.service.OrderDomainService orderDomainService,
            @Qualifier("scenarioPaymentClient") com.xm.scenario.payment.client.PaymentClient paymentClient,
            @Qualifier("scenarioInventoryClient") com.xm.scenario.inventory.client.InventoryClient inventoryClient,
            TccCoordinator tccCoordinator) {
        return new OrderSubmitWithPaymentTccService(
                orderDomainService, paymentClient, inventoryClient, tccCoordinator);
    }

    // ---------- 业务用例：Saga 版 submit-with-payment ----------
    @Bean
    @ConditionalOnBean(SagaOrchestrator.class)
    public OrderSubmitWithPaymentSagaService orderSubmitWithPaymentSagaService(
            com.xm.scenario.order.domain.service.OrderDomainService orderDomainService,
            @Qualifier("scenarioPaymentClient") com.xm.scenario.payment.client.PaymentClient paymentClient,
            @Qualifier("scenarioInventoryClient") com.xm.scenario.inventory.client.InventoryClient inventoryClient,
            SagaOrchestrator sagaOrchestrator) {
        return new OrderSubmitWithPaymentSagaService(
                orderDomainService, paymentClient, inventoryClient, sagaOrchestrator);
    }

    // ---------- 防腐层桩（TCC/Saga 业务用例依赖） ----------
    @Bean("scenarioPaymentClientStub")
    @ConditionalOnExpression("'${xm.scenario.tcc:}'.length()>0 || '${xm.scenario.saga:}'.length()>0")
    public com.xm.scenario.payment.client.PaymentClient scenarioPaymentClientStub() {
        return new StubPaymentClient();
    }

    @Bean("scenarioPaymentClient")
    @ConditionalOnExpression("'${xm.scenario.tcc:}'.length()>0 || '${xm.scenario.saga:}'.length()>0")
    @ConditionalOnProperty(name = "xm.scenario.circuit-breaker", havingValue = "false", matchIfMissing = true)
    public com.xm.scenario.payment.client.PaymentClient scenarioPaymentClientDirect(
            @Qualifier("scenarioPaymentClientStub") com.xm.scenario.payment.client.PaymentClient stub) {
        return stub;
    }

    @Bean("scenarioInventoryClientStub")
    @ConditionalOnExpression("'${xm.scenario.tcc:}'.length()>0 || '${xm.scenario.saga:}'.length()>0")
    public com.xm.scenario.inventory.client.InventoryClient scenarioInventoryClientStub() {
        return new StubInventoryClient();
    }

    @Bean("scenarioInventoryClient")
    @ConditionalOnExpression("'${xm.scenario.tcc:}'.length()>0 || '${xm.scenario.saga:}'.length()>0")
    @ConditionalOnProperty(name = "xm.scenario.circuit-breaker", havingValue = "false", matchIfMissing = true)
    public com.xm.scenario.inventory.client.InventoryClient scenarioInventoryClientDirect(
            @Qualifier("scenarioInventoryClientStub") com.xm.scenario.inventory.client.InventoryClient stub) {
        return stub;
    }
}
