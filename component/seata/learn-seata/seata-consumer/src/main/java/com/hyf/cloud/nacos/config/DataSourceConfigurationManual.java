package com.hyf.cloud.nacos.config;

import io.seata.rm.datasource.DataSourceProxy;
import io.seata.rm.datasource.xa.DataSourceProxyXA;
import io.seata.spring.annotation.GlobalTransactionScanner;
import io.seata.spring.boot.autoconfigure.properties.SeataProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.annotation.Resource;
import javax.sql.DataSource;

/**
 * 针对于spring环境需要
 *
 * @author baB_hyf
 * @date 2021/08/24
 */
// @Configuration
public class DataSourceConfigurationManual {

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private SeataProperties seataProperties;

    @Primary
    @Bean
    public DataSourceProxy dataSource(DataSource dataSource) {

        // AT模式
        return new DataSourceProxy(dataSource);

        // XA模式
        // return new DataSourceProxyXA(dataSource);
    }

    @Bean
    public GlobalTransactionScanner globalTransactionScanner() {
        String applicationName = applicationContext.getEnvironment().getProperty("spring.application.name");
        String txServiceGroup = seataProperties.getTxServiceGroup();
        if (txServiceGroup == null || "".equals(txServiceGroup)) {
            txServiceGroup = applicationName + "-group";
            seataProperties.setTxServiceGroup(txServiceGroup);
        }

        return new GlobalTransactionScanner(applicationName, txServiceGroup);
    }
}
