package com.hyf.cloud.nacos.config;

import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.config.DbStateMachineConfig;
import io.seata.saga.engine.impl.ProcessCtrlStateMachineEngine;
import io.seata.saga.rm.StateMachineEngineHolder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceArrayPropertyEditor;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * saga
 *
 * @author baB_hyf
 * @date 2021/08/28
 */
@Configuration
public class StateMachineConfigurationLevelup implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    private DbStateMachineConfig dbStateMachineConfig;

    @Bean
    public StateMachineEngine stateMachineEngine(DataSource dataSource, ObjectProvider<ThreadPoolExecutor> stateMachineExecutor) throws Exception {

        DbStateMachineConfig dbStateMachineConfig = new DbStateMachineConfig();

        // datasource
        dbStateMachineConfig.setDataSource(dataSource);

        // resources
        String filePattern = "state-lang/*.json";
        ResourceArrayPropertyEditor editor = new ResourceArrayPropertyEditor();
        editor.setAsText(filePattern);
        dbStateMachineConfig.setResources((Resource[]) editor.getValue());

        // async need
        dbStateMachineConfig.setEnableAsync(true);
        // 事件驱动执行时使用的线程池, 如果所有状态机都同步执行且不存在循环任务可以不需要
        dbStateMachineConfig.setThreadPoolExecutor(stateMachineExecutor.getIfAvailable());

        // set in new
        // dbStateMachineConfig.setApplicationId("saga-application");
        // dbStateMachineConfig.setTxServiceGroup("hyf_tc_group");
        // dbStateMachineConfig.setSagaBranchRegisterEnable(false);
        // dbStateMachineConfig.setSagaJsonParser("fastjson");
        // dbStateMachineConfig.setSagaRetryPersistModeUpdate(false);
        // dbStateMachineConfig.setSagaCompensatePersistModeUpdate(false);

        // option
        dbStateMachineConfig.setSagaBranchRegisterEnable(true);

        ProcessCtrlStateMachineEngine processCtrlStateMachineEngine = new ProcessCtrlStateMachineEngine();
        processCtrlStateMachineEngine.setStateMachineConfig(dbStateMachineConfig);

        // set before config afterPropertiesSet to prevent server pre request
        // Seata Server进行事务恢复时需要通过这个Holder拿到stateMachineEngine实例
        new StateMachineEngineHolder().setStateMachineEngine(processCtrlStateMachineEngine);

        dbStateMachineConfig.setApplicationContext(this.applicationContext);
        dbStateMachineConfig.afterPropertiesSet();
        this.dbStateMachineConfig = dbStateMachineConfig;

        return processCtrlStateMachineEngine;
    }

    @Bean
    public ThreadPoolExecutor stateMachineExecutor() {
        ThreadPoolExecutorFactoryBean threadPoolExecutorFactoryBean = new ThreadPoolExecutorFactoryBean();
        threadPoolExecutorFactoryBean.setThreadNamePrefix("SAGA_ASYNC_EXEC_");
        threadPoolExecutorFactoryBean.setCorePoolSize(1);
        threadPoolExecutorFactoryBean.setMaxPoolSize(20);
        threadPoolExecutorFactoryBean.afterPropertiesSet();
        return (ThreadPoolExecutor) threadPoolExecutorFactoryBean.getObject();
    }

    @PreDestroy
    public void destroy() throws Exception {
        dbStateMachineConfig.destroy();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
