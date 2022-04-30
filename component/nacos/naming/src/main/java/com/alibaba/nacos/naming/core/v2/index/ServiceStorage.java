/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManagerDelegate;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataManager;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 存储所有的Service信息，内部包含了Instance信息
 * <p>
 * 主要通过维护的两个映射关系来获取
 *
 * Service storage.
 *
 * @author xiweng.yy
 */
@Component
public class ServiceStorage {
    
    // 客户端索引，方便直接获取客户端信息
    private final ClientServiceIndexesManager serviceIndexesManager;
    
    // 客户端管理器，通过clientId获取具体的客户端数据
    private final ClientManager clientManager;
    
    private final SwitchDomain switchDomain;
    
    // 获取实例元数据信息
    private final NamingMetadataManager metadataManager;
    
    // service -> service and instances
    private final ConcurrentMap<Service, ServiceInfo> serviceDataIndexes;
    
    // service -> clusterNameSet
    private final ConcurrentMap<Service, Set<String>> serviceClusterIndex;
    
    public ServiceStorage(ClientServiceIndexesManager serviceIndexesManager, ClientManagerDelegate clientManager,
            SwitchDomain switchDomain, NamingMetadataManager metadataManager) {
        this.serviceIndexesManager = serviceIndexesManager;
        this.clientManager = clientManager;
        this.switchDomain = switchDomain;
        this.metadataManager = metadataManager;
        this.serviceDataIndexes = new ConcurrentHashMap<>();
        this.serviceClusterIndex = new ConcurrentHashMap<>();
    }
    
    public Set<String> getClusters(Service service) {
        return serviceClusterIndex.getOrDefault(service, new HashSet<>());
    }
    
    public ServiceInfo getData(Service service) {
        // 没有就初始化个空的
        return serviceDataIndexes.containsKey(service) ? serviceDataIndexes.get(service) : getPushData(service);
    }
    
    // 新建一个service信息，包含所有的实例
    // 一般用于服务端主动推送使用，需要保证最新的服务信息被推送，不使用缓存，同时每次推送也会更新本地缓存
    public ServiceInfo getPushData(Service service) {
        ServiceInfo result = emptyServiceInfo(service);
        // 服务没有注册过
        if (!ServiceManager.getInstance().containSingleton(service)) {
            return result;
        }
        // 给服务添加上所有的实例
        result.setHosts(getAllInstancesFromIndex(service));
        serviceDataIndexes.put(service, result);
        return result;
    }
    
    public void removeData(Service service) {
        serviceDataIndexes.remove(service);
        serviceClusterIndex.remove(service);
    }
    
    private ServiceInfo emptyServiceInfo(Service service) {
        ServiceInfo result = new ServiceInfo();
        result.setName(service.getName());
        result.setGroupName(service.getGroup());
        result.setLastRefTime(System.currentTimeMillis());
        result.setCacheMillis(switchDomain.getDefaultPushCacheMillis());
        return result;
    }
    
    private List<Instance> getAllInstancesFromIndex(Service service) {
        Set<Instance> result = new HashSet<>();
        Set<String> clusters = new HashSet<>();
        // 通过服务索引，获取所有的客户端id
        for (String each : serviceIndexesManager.getAllClientsRegisteredService(service)) {
            // 再通过客户端id，找到客户端内注册的所有实例对象
            Optional<InstancePublishInfo> instancePublishInfo = getInstanceInfo(each, service);
            if (instancePublishInfo.isPresent()) {
                Instance instance = parseInstance(service, instancePublishInfo.get());
                result.add(instance);
                clusters.add(instance.getClusterName());
            }
        }
        // 顺便更新下
        serviceClusterIndex.put(service, clusters);
        return new LinkedList<>(result);
    }
    
    private Optional<InstancePublishInfo> getInstanceInfo(String clientId, Service service) {
        Client client = clientManager.getClient(clientId);
        if (null == client) {
            return Optional.empty();
        }
        return Optional.ofNullable(client.getInstancePublishInfo(service));
    }
    
    private Instance parseInstance(Service service, InstancePublishInfo instanceInfo) {
        Instance result = InstanceUtil.parseToApiInstance(service, instanceInfo);
        Optional<InstanceMetadata> metadata = metadataManager
                .getInstanceMetadata(service, instanceInfo.getMetadataId());
        metadata.ifPresent(instanceMetadata -> InstanceUtil.updateInstanceMetadata(result, instanceMetadata));
        return result;
    }
}
