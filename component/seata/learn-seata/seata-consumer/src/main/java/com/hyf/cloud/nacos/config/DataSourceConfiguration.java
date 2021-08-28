package com.hyf.cloud.nacos.config;

import io.seata.spring.annotation.datasource.EnableAutoDataSourceProxy;
import org.springframework.context.annotation.Configuration;

/**
 * @author baB_hyf
 * @date 2021/08/24
 */
// @Configuration
@EnableAutoDataSourceProxy(useJdkProxy = false, dataSourceProxyMode = "AT")
public class DataSourceConfiguration {

}
