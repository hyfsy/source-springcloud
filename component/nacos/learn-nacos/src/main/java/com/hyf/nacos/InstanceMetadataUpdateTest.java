package com.hyf.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author baB_hyf
 * @date 2023/06/12
 */
public class InstanceMetadataUpdateTest {

    public static void main(String[] args) throws Exception {

        Properties properties = new Properties();

        System.setProperty(CommonParams.NAMING_REQUEST_TIMEOUT, "100000");

        String serverAddr = "127.0.0.1:8848";
        String serviceName = "example-service";
        String groupName = "DEFAULT_GROUP";
        String clusterName = "TEST_CLUSTER";

        // 获取命名服务对象
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.setProperty(CommonParams.NAMING_REQUEST_TIMEOUT, "100000");
        NamingService namingService = NacosFactory.createNamingService(properties);

        // 实例
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(10001);
        instance.setServiceName(serviceName);
        instance.setClusterName(clusterName);
        instance.setWeight(2.0);
        Map<String, String> instanceMetadataMap = new HashMap<>();
        instanceMetadataMap.put("type", "test-instance");
        instanceMetadataMap.put("account", "hyf");
        instance.setMetadata(instanceMetadataMap);

        // 注册实例
        namingService.registerInstance(serviceName, groupName, instance);

        Thread.sleep(2000L);

        List<Instance> instances0 = namingService.selectInstances(serviceName, groupName, true);

        System.out.println("================================================");
        for (Instance instance1 : instances0) {
            System.out.println(instance1.getMetadata());
        }

        Thread.sleep(2000L);

        instance.getMetadata().clear();
        namingService.registerInstance(serviceName, groupName, instance);


        Thread.sleep(1000L);


        List<Instance> instances00 = namingService.selectInstances(serviceName, groupName, true);

        System.out.println("================================================");
        for (Instance instance1 : instances00) {
            System.out.println(instance1.getMetadata());
        }

        instances00 = namingService.selectInstances(serviceName, groupName, true, false);

        System.out.println("================================================");
        for (Instance instance1 : instances00) {
            System.out.println(instance1.getMetadata());
        }

        // 获取维护服务对象
        NamingMaintainService maintainService = NacosFactory.createMaintainService(serverAddr);

        Thread.sleep(1000L);

        instance.getMetadata().clear();
        maintainService.updateInstance(serviceName, groupName, instance);

        Thread.sleep(1000L);

        List<Instance> instances = namingService.selectInstances(serviceName, groupName, true);

        // instance.getMetadata().put("account", "test");
        // maintainService.updateInstance(serviceName, groupName, instance);

        Thread.sleep(1000L);

        List<Instance> instances2 = namingService.selectInstances(serviceName, groupName, true);

        // Thread.sleep(10000L);

        maintainService.shutDown();
        namingService.shutDown();

    }
}
