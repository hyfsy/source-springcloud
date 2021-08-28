package com.hyf.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author baB_hyf
 * @date 2021/07/25
 */
public class DiscoveryTest {

    public static void main(String[] args) throws NacosException, InterruptedException {

        String serverAddr = "127.0.0.1:8848";
        String serviceName = "example-service";
        String groupName = "DEFAULT_GROUP";
        String clusterName = "TEST_CLUSTER";

        // 获取命名服务对象
        NamingService namingService = NacosFactory.createNamingService(serverAddr);

        // 注册服务
        namingService.registerInstance(serviceName, groupName, "127.0.0.0", 10000, clusterName);

        // 实例
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(10001);
        instance.setServiceName(serviceName);
        instance.setClusterName(clusterName);
        instance.setHealthy(false);
        instance.setWeight(2.0);
        Map<String, String> instanceMetadataMap = new HashMap<>();
        instanceMetadataMap.put("type", "test-instance");
        instance.setMetadata(instanceMetadataMap);

        // 服务
        Service service = new Service();
        service.setAppName(serviceName); // 应用名
        service.setName(serviceName); // 服务名
        service.setGroupName(groupName); // 组名
        service.setProtectThreshold(0.8F); // 保护阈值
        Map<String, String> serviceMetadataMap = new HashMap<>();
        serviceMetadataMap.put("type", "test-service");
        service.setMetadata(serviceMetadataMap);

        // 集群
        Cluster cluster = new Cluster();
        cluster.setName(clusterName);
        cluster.setServiceName(serviceName);
        Http checker = new Http();
        checker.setExpectedResponseCode(400);
        checker.setHeaders("User-Agent|Nacos");
        checker.setPath("/xxx.html");
        cluster.setHealthChecker(checker);
        Map<String, String> clusterMetadataMap = new HashMap<>();
        clusterMetadataMap.put("type", "test-cluster");
        cluster.setMetadata(clusterMetadataMap);

        // 注册实例
        namingService.registerInstance(serviceName, groupName, instance);

        // 取消注册实例
        namingService.deregisterInstance(serviceName, "127.0.0.0", 10000, clusterName);

        // 获取所有实例
        List<Instance> allInstanceList = namingService.getAllInstances(serviceName, groupName,
                Collections.singletonList(clusterName), true /* 订阅 */);
        System.out.println(allInstanceList);

        // 获取一个健康的实例
        Instance healthyInstance = namingService.selectOneHealthyInstance(serviceName, groupName);
        System.out.println(healthyInstance);

        // 订阅，监听服务列表的变化
        namingService.subscribe(serviceName, groupName,
                Collections.singletonList(clusterName), new EventListener() {
                    @Override
                    public void onEvent(Event event) {
                        if (event instanceof NamingEvent) {
                            NamingEvent namingEvent = (NamingEvent) event;
                            String newServiceName = namingEvent.getServiceName();
                            String newGroupName = namingEvent.getGroupName();
                            String newClusterName = namingEvent.getClusters();
                            List<Instance> newInstanceList = namingEvent.getInstances();
                            System.out.println(String.format("Service changed: %s, group: %s, cluster: %s, instances: %s",
                                    newServiceName, newGroupName, newClusterName, newInstanceList));
                        }
                    }
                });

        // 取消订阅
        namingService.unsubscribe(serviceName, event -> {
        });

        // 关闭服务内的http线程池
        namingService.shutDown();

        Thread.currentThread().join();
    }
}
