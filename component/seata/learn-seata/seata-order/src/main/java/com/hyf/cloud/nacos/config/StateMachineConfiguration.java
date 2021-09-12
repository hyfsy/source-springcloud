// package com.hyf.cloud.nacos.config;
//
// import io.seata.saga.engine.StateMachineConfig;
// import io.seata.saga.engine.StateMachineEngine;
// import io.seata.saga.engine.config.DbStateMachineConfig;
// import io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
// import io.seata.saga.rm.StateMachineEngineHolder;
// import org.springframework.beans.factory.ObjectProvider;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.core.io.Resource;
// import org.springframework.core.io.support.ResourceArrayPropertyEditor;
// import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
//
// import javax.sql.DataSource;
// import java.util.concurrent.ThreadPoolExecutor;
//
// /**
//  * saga
//  *
//  * @author baB_hyf
//  * @date 2021/08/28
//  */
// @Configuration
// public class StateMachineConfiguration {
//
//     @Bean
//     public StateMachineEngineHolder stateMachineEngineHolder(StateMachineEngine stateMachineEngine) {
//         StateMachineEngineHolder stateMachineEngineHolder = new StateMachineEngineHolder();
//         stateMachineEngineHolder.setStateMachineEngine(stateMachineEngine);
//         return stateMachineEngineHolder;
//     }
//
//     @Bean
//     public StateMachineEngine stateMachineEngine(StateMachineConfig stateMachineConfig) {
//         ProcessCtrlStateMachineEngine processCtrlStateMachineEngine = new ProcessCtrlStateMachineEngine();
//         processCtrlStateMachineEngine.setStateMachineConfig(stateMachineConfig);
//
//         return processCtrlStateMachineEngine;
//     }
//
//     @Bean
//     public StateMachineConfig stateMachineConfig(DataSource dataSource, ObjectProvider<ThreadPoolExecutor> stateMachineExecutor) {
//         DbStateMachineConfig dbStateMachineConfig = new DbStateMachineConfig();
//
//         // datasource
//         dbStateMachineConfig.setDataSource(dataSource);
//
//         // resources
//         String filePattern = "state-lang/*.json";
//         ResourceArrayPropertyEditor editor = new ResourceArrayPropertyEditor();
//         editor.setAsText(filePattern);
//         dbStateMachineConfig.setResources((Resource[]) editor.getValue());
//
//         // async need
//         dbStateMachineConfig.setEnableAsync(true);
//         dbStateMachineConfig.setThreadPoolExecutor(stateMachineExecutor.getIfAvailable());
//
//         // set in new
//         // dbStateMachineConfig.setApplicationId("saga-application");
//         // dbStateMachineConfig.setTxServiceGroup("hyf_tc_group");
//         // dbStateMachineConfig.setSagaBranchRegisterEnable(false);
//         // dbStateMachineConfig.setSagaJsonParser("fastjson");
//         // dbStateMachineConfig.setSagaRetryPersistModeUpdate(false);
//         // dbStateMachineConfig.setSagaCompensatePersistModeUpdate(false);
//         return dbStateMachineConfig;
//     }
//
//     @Bean
//     public ThreadPoolExecutor stateMachineExecutor() {
//         ThreadPoolExecutorFactoryBean threadPoolExecutorFactoryBean = new ThreadPoolExecutorFactoryBean();
//         threadPoolExecutorFactoryBean.setThreadNamePrefix("SAGA_ASYNC_EXEC_");
//         threadPoolExecutorFactoryBean.setCorePoolSize(1);
//         threadPoolExecutorFactoryBean.setMaxPoolSize(20);
//         threadPoolExecutorFactoryBean.afterPropertiesSet();
//         return (ThreadPoolExecutor) threadPoolExecutorFactoryBean.getObject();
//     }
// }
