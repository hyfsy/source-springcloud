package com.hyf.cloud.nacos;

import io.seata.spring.annotation.datasource.EnableAutoDataSourceProxy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * -javaagent:E:\study\idea4\source-springcloud\cloud-resources\skywalking\apache-skywalking-apm-8.5.0\agent\skywalking-agent.jar -Dskywalking.agent.service_name=seata_client_money -Dskywalking.plugin.jdbc.trace_sql_parameters=true
 *
 * @author baB_hyf
 * @date 2021/08/23
 */
@SpringBootApplication
@EnableAutoDataSourceProxy
@EnableFeignClients
public class MoneyApplication {

    // http://localhost:8330/money/deduct?userId=1&money=100

    public static void main(String[] args) {
        SpringApplication.run(MoneyApplication.class, args);
    }
}
