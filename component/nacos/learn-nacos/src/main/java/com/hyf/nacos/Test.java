package com.hyf.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.selector.ExpressionSelector;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author baB_hyf
 * @date 2021/08/02
 */
public class Test {
    
    public static final String serverAddr = "127.0.0.1:8848";
    public static final String dataId = "example";
    public static final String group = "DEFAULT_GROUP";
    
    public static final String serviceName = "example-service";
    public static final String groupName = "DEFAULT_GROUP";
    public static final String clusterName = "TEST_CLUSTER";
    
    public static void main(String[] args) throws Exception {
        String path = Test.class.getResource("/").getPath();
        String localPath = path.substring(1, path.length() - 1);
        
        System.setProperty("JM.LOG.PATH", localPath);
        System.setProperty("JM.SNAPSHOT.PATH", localPath);
        
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
//        properties.put(PropertyKeyConst.ENDPOINT, "localhost");

//        config(properties);
        naming(properties);
//        maintain(properties);
    }
    
    public static void config(Properties properties) throws Exception {
        ConfigService configService = NacosFactory.createConfigService(properties);
        System.out.println(configService.getServerStatus());
        String config = configService.getConfig(dataId, group, 3000L);
        System.out.println(config);
//        System.out.println(System.getProperty("project.name"));
    
        // ??????????????????
        configService.addListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
        
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println("Get config info: " + configInfo);
            }
        });
    
        // ????????????
        configService.removeListener(dataId, group, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
        
            @Override
            public void receiveConfigInfo(String configInfo) {
            
            }
        });
    
        // ??????????????? - ??????????????????
        String content = "hello=changed hello value";
        boolean success = configService.publishConfig(dataId, group, content,
                ConfigType.getDefaultType().getType());
        System.out.println("publish success: " + success);
        
        // ??????????????????http?????????
//        configService.shutDown();
    
        Thread.currentThread().join();
    }
    
    public static void naming(Properties properties) throws Exception {
    
        // ????????????????????????
        NamingService namingService = NacosFactory.createNamingService(serverAddr);
    
        // ????????????
        namingService.registerInstance(serviceName, groupName, "127.0.0.0", 10000, clusterName);
    
        // ??????
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
    
        // ??????
        Service service = new Service();
        service.setAppName(serviceName); // ?????????
        service.setName(serviceName); // ?????????
        service.setGroupName(groupName); // ??????
        service.setProtectThreshold(0.8F); // ????????????
        Map<String, String> serviceMetadataMap = new HashMap<>();
        serviceMetadataMap.put("type", "test-service");
        service.setMetadata(serviceMetadataMap);
    
        // ??????
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
    
        // ????????????
//        namingService.registerInstance(serviceName, groupName, instance);
    
        // ??????????????????
        List<Instance> allInstanceList = namingService.getAllInstances(serviceName, groupName,
                Collections.singletonList(clusterName), true /* ?????? */);
        System.out.println(allInstanceList);
    
//        List<Instance> instances = namingService.selectInstances(serviceName, groupName, false, true);
//        System.out.println(instances);

        // ???????????????????????????
        if (!allInstanceList.isEmpty()) {
            Instance healthyInstance = namingService.selectOneHealthyInstance(serviceName, groupName, Collections.singletonList(clusterName));
            System.out.println(healthyInstance);
        }
    
        // ????????????????????????????????????
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
    
        // ????????????
        namingService.unsubscribe(serviceName, event -> {
            System.out.println(1);
        });
    
        ListView<String> servicesOfServer = namingService.getServicesOfServer(0, 999);
        System.out.println(servicesOfServer);
    
        // ??????????????????
//        namingService.deregisterInstance(serviceName, "127.0.0.0", 10000, clusterName);
    
        // ??????????????????http?????????
//        namingService.shutDown();
    
        Thread.currentThread().join();
    }
    
    public static void maintain(Properties properties) throws Exception {
    
        // ????????????????????????
        NamingMaintainService maintainService = NacosFactory.createMaintainService(serverAddr);
    
        // ????????????
//        Service service = maintainService.queryService(serviceName, groupName);
//        System.out.println(service);
    
        // ????????????
        maintainService.createService(serviceName, groupName, 2.0F, "CONSUMER.label.test=PROVIDER.label.test");
    
        // ????????????
        maintainService.deleteService(serviceName, groupName);
    
        // ????????????
        maintainService.updateService(new Service(), new ExpressionSelector());
    
        // ????????????
        maintainService.updateInstance(serviceName, groupName, new Instance());
    
        // ??????????????????http?????????
//        maintainService.shutDown();
    
        Thread.currentThread().join();
    }
    
}
