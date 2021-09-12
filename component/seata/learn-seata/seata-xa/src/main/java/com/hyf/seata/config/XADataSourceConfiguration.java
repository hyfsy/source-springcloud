package com.hyf.seata.config;

import com.alibaba.druid.pool.DruidDataSource;
import io.seata.rm.datasource.xa.DataSourceProxyXA;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author baB_hyf
 * @date 2021/09/05
 */
// @Configuration
public class XADataSourceConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DruidDataSource druidDataSource() {
        return new DruidDataSource();
    }

    // seata 通过数据源代理实现XA事务
    @Bean
    public DataSource dataSource(DruidDataSource druidDataSource) {
        return new DataSourceProxyXA(druidDataSource);
    }
}
