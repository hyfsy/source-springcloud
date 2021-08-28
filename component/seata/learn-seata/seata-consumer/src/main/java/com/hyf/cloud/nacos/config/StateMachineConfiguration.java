package com.hyf.cloud.nacos.config;

import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.config.DbStateMachineConfig;
import io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

import javax.sql.DataSource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * saga
 *
 * @author baB_hyf
 * @date 2021/08/28
 */
// @Configuration
public class StateMachineConfiguration {

    @Bean
    public StateMachineEngine stateMachineEngine(DataSource dataSource, ThreadPoolExecutor stateMachineExecutor) {
        ProcessCtrlStateMachineEngine processCtrlStateMachineEngine = new ProcessCtrlStateMachineEngine();
        processCtrlStateMachineEngine.setStateMachineConfig(stateMachineConfig(dataSource, stateMachineExecutor));
        return processCtrlStateMachineEngine;
    }

    @Bean
    public StateMachineConfig stateMachineConfig(DataSource dataSource, ThreadPoolExecutor stateMachineExecutor) {
        DbStateMachineConfig dbStateMachineConfig = new DbStateMachineConfig();
        dbStateMachineConfig.setDataSource(dataSource);
        dbStateMachineConfig.setResources(new Resource[]{new ClassPathResource("state-lang/*.json")});
        dbStateMachineConfig.setEnableAsync(true);
        dbStateMachineConfig.setThreadPoolExecutor(stateMachineExecutor);
        dbStateMachineConfig.setApplicationId("saga-application");
        dbStateMachineConfig.setTxServiceGroup("hyf_tc_group");
        dbStateMachineConfig.setSagaBranchRegisterEnable(false);
        dbStateMachineConfig.setSagaJsonParser("fastjson");
        dbStateMachineConfig.setSagaRetryPersistModeUpdate(false);
        dbStateMachineConfig.setSagaCompensatePersistModeUpdate(false);
        return dbStateMachineConfig;
    }

    @Bean
    public ThreadPoolExecutorFactoryBean stateMachineExecutor() {
        ThreadPoolExecutorFactoryBean threadPoolExecutorFactoryBean = new ThreadPoolExecutorFactoryBean();
        threadPoolExecutorFactoryBean.setThreadNamePrefix("SAGA_ASYNC_EXEC_");
        threadPoolExecutorFactoryBean.setCorePoolSize(1);
        threadPoolExecutorFactoryBean.setMaxPoolSize(20);
        return threadPoolExecutorFactoryBean;
    }
}
