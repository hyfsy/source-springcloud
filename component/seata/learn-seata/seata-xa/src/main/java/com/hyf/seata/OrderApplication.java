package com.hyf.seata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * -javaagent:E:\study\idea4\source-springcloud\cloud-resources\skywalking\apache-skywalking-apm-8.5.0\agent\skywalking-agent.jar -Dskywalking.agent.service_name=seata_client_order -Dskywalking.plugin.jdbc.trace_sql_parameters=true
 *
 * @author baB_hyf
 * @date 2021/08/23
 */
@SpringBootApplication
// @EnableAutoDataSourceProxy
@EnableFeignClients
public class OrderApplication {

    // http://localhost:8329/order/create?userId=1&storeId=1&number=1&ex=0

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
