package com.xm.transaction.seata.saga;

import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.engine.config.DbStateMachineConfig;
import org.apache.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.apache.seata.saga.rm.StateMachineEngineHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

/**
 * Seata Saga StateMachineEngine 配置。saga=seata 且 DataSource 可用时生效。
 * 需 statelang/*.json 与 saga 状态表（见 seata-saga-engine-store 的 sql）。
 */
@Configuration
@ConditionalOnProperty(name = "xm.scenario.saga", havingValue = "seata")
public class SeataSagaEngineConfig {

    @Bean
    public StateMachineEngine stateMachineEngine(DataSource dataSource) throws Exception {
        DbStateMachineConfig config = new DbStateMachineConfig();
        config.setDataSource(dataSource);
        config.setResources(new String[]{"classpath*:statelang/*.json"});
        config.setEnableAsync(false);
        config.setApplicationId("xm-service");
        config.setTxServiceGroup("default_tx_group");
        config.setSagaBranchRegisterEnable(false);

        ProcessCtrlStateMachineEngine engine = new ProcessCtrlStateMachineEngine();
        engine.setStateMachineConfig(config);

        StateMachineEngineHolder.setStateMachineEngine(engine);

        return engine;
    }
}
