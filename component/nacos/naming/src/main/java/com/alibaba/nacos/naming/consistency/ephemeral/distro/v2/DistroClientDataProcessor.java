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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.SmartSubscriber;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataProcessor;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataStorage;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncData;
import com.alibaba.nacos.naming.core.v2.client.ClientSyncDatumSnapshot;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.core.v2.event.client.ClientEvent;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisherFactory;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Distro processor for v2.
 * <p>
 * 处理当前节点更新的数据，同步到其他节点，v2版本的rpc使用
 * <p>
 * 该类既能负责发送distro请求到其他节点，又能处理其他节点发送过来的请求
 *
 * @author xiweng.yy
 */
public class DistroClientDataProcessor extends SmartSubscriber implements DistroDataStorage, DistroDataProcessor {
    
    public static final String TYPE = "Nacos:Naming:v2:ClientData";
    
    private final ClientManager clientManager;
    
    private final DistroProtocol distroProtocol;
    
    private final UpgradeJudgement upgradeJudgement;
    
    private volatile boolean isFinishInitial;
    
    public DistroClientDataProcessor(ClientManager clientManager, DistroProtocol distroProtocol,
            UpgradeJudgement upgradeJudgement) {
        this.clientManager = clientManager;
        this.distroProtocol = distroProtocol;
        this.upgradeJudgement = upgradeJudgement;
        NotifyCenter.registerSubscriber(this, NamingEventPublisherFactory.getInstance());
    }
    
    @Override
    public void finishInitial() {
        isFinishInitial = true;
    }
    
    @Override
    public boolean isFinishInitial() {
        return isFinishInitial;
    }
    
    @Override
    public List<Class<? extends Event>> subscribeTypes() {
        List<Class<? extends Event>> result = new LinkedList<>();
        result.add(ClientEvent.ClientChangedEvent.class);
        result.add(ClientEvent.ClientDisconnectEvent.class);
        result.add(ClientEvent.ClientVerifyFailedEvent.class);
        return result;
    }
    
    // 接受本地的服务实例数据的更新或校验任务，通过distroProtocol发送对应的请求到其他节点
    // 用于进行数据同步，维护数据一致
    @Override
    public void onEvent(Event event) {
        if (EnvUtil.getStandaloneMode()) {
            return;
        }
        if (!upgradeJudgement.isUseGrpcFeatures()) {
            return;
        }
        if (event instanceof ClientEvent.ClientVerifyFailedEvent) {
            syncToVerifyFailedServer((ClientEvent.ClientVerifyFailedEvent) event);
        } else {
            syncToAllServer((ClientEvent) event);
        }
    }
    
    private void syncToVerifyFailedServer(ClientEvent.ClientVerifyFailedEvent event) {
        Client client = clientManager.getClient(event.getClientId());
        if (null == client || !client.isEphemeral() || !clientManager.isResponsibleClient(client)) {
            return;
        }
        DistroKey distroKey = new DistroKey(client.getClientId(), TYPE);
        // Verify failed data should be sync directly.
        distroProtocol.syncToTarget(distroKey, DataOperation.ADD, event.getTargetServer(), 0L);
    }
    
    private void syncToAllServer(ClientEvent event) {
        Client client = event.getClient();
        // Only ephemeral data sync by Distro, persist client should sync by raft.
        if (null == client || !client.isEphemeral() || !clientManager.isResponsibleClient(client)) {
            return;
        }
        // 发送同步请求到其他节点
        // 客户端连接断开
        if (event instanceof ClientEvent.ClientDisconnectEvent) {
            DistroKey distroKey = new DistroKey(client.getClientId(), TYPE);
            distroProtocol.sync(distroKey, DataOperation.DELETE);
        }
        // 添加了新的服务信息/Client
        else if (event instanceof ClientEvent.ClientChangedEvent) {
            DistroKey distroKey = new DistroKey(client.getClientId(), TYPE);
            distroProtocol.sync(distroKey, DataOperation.CHANGE);
        }
    }
    
    @Override
    public String processType() {
        return TYPE;
    }
    
    @Override
    public boolean processData(DistroData distroData) {
        switch (distroData.getType()) {
            case ADD:
            case CHANGE:
                ClientSyncData clientSyncData = ApplicationUtils.getBean(Serializer.class)
                        .deserialize(distroData.getContent(), ClientSyncData.class);
                handlerClientSyncData(clientSyncData);
                return true;
            case DELETE:
                String deleteClientId = distroData.getDistroKey().getResourceKey();
                Loggers.DISTRO.info("[Client-Delete] Received distro client sync data {}", deleteClientId);
                clientManager.clientDisconnected(deleteClientId);
                return true;
            default:
                return false;
        }
    }
    
    // 更新客户端
    private void handlerClientSyncData(ClientSyncData clientSyncData) {
        Loggers.DISTRO.info("[Client-A|dd] Received distro client sync data {}", clientSyncData.getClientId());
        // 本地创建一个非直连的Client isNative=false，意思是从其他节点中同步过来的
        clientManager.syncClientConnected(clientSyncData.getClientId(), clientSyncData.getAttributes());
        Client client = clientManager.getClient(clientSyncData.getClientId());
        upgradeClient(client, clientSyncData);
    }
    
    private void upgradeClient(Client client, ClientSyncData clientSyncData) {
        List<String> namespaces = clientSyncData.getNamespaces();
        List<String> groupNames = clientSyncData.getGroupNames();
        List<String> serviceNames = clientSyncData.getServiceNames();
        List<InstancePublishInfo> instances = clientSyncData.getInstancePublishInfos();
        Set<Service> syncedService = new HashSet<>();
        // 新增/更新
        for (int i = 0; i < namespaces.size(); i++) {
            Service service = Service.newService(namespaces.get(i), groupNames.get(i), serviceNames.get(i));
            Service singleton = ServiceManager.getInstance().getSingleton(service);
            syncedService.add(singleton);
            InstancePublishInfo instancePublishInfo = instances.get(i);
            // 远程发过来的实例数据是否已存在，不存在则处理
            if (!instancePublishInfo.equals(client.getInstancePublishInfo(singleton))) {
                // 其他节点接收到还会发布同步事件？不会影响性能？
                client.addServiceInstance(singleton, instancePublishInfo);
                NotifyCenter.publishEvent(
                        new ClientOperationEvent.ClientRegisterServiceEvent(singleton, client.getClientId()));
            }
        }
        // 删除
        for (Service each : client.getAllPublishedService()) {
            if (!syncedService.contains(each)) {
                client.removeServiceInstance(each);
                NotifyCenter.publishEvent(
                        new ClientOperationEvent.ClientDeregisterServiceEvent(each, client.getClientId()));
            }
        }
    }
    
    // 处理来自其他节点的数据，校验客户端，进行心跳续期
    @Override
    public boolean processVerifyData(DistroData distroData, String sourceAddress) {
        DistroClientVerifyInfo verifyData = ApplicationUtils.getBean(Serializer.class)
                .deserialize(distroData.getContent(), DistroClientVerifyInfo.class);
        if (clientManager.verifyClient(verifyData.getClientId())) {
            return true;
        }
        Loggers.DISTRO.info("client {} is invalid, get new client from {}", verifyData.getClientId(), sourceAddress);
        return false;
    }
    
    // 本地处理从其他节点拉取到的快照数据
    @Override
    public boolean processSnapshot(DistroData distroData) {
        ClientSyncDatumSnapshot snapshot = ApplicationUtils.getBean(Serializer.class)
                .deserialize(distroData.getContent(), ClientSyncDatumSnapshot.class);
        for (ClientSyncData each : snapshot.getClientSyncDataList()) {
            // 本地更新
            handlerClientSyncData(each);
        }
        return true;
    }
    
    // 获取本地的客户端相关的所有数据，然后发送给其他的对等节点进行同步
    @Override
    public DistroData getDistroData(DistroKey distroKey) {
        Client client = clientManager.getClient(distroKey.getResourceKey());
        if (null == client) {
            return null;
        }
        byte[] data = ApplicationUtils.getBean(Serializer.class).serialize(client.generateSyncData());
        return new DistroData(distroKey, data);
    }
    
    // 获取本地全量的快照数据，返回给其他节点进行全量初始化
    @Override
    public DistroData getDatumSnapshot() {
        List<ClientSyncData> datum = new LinkedList<>();
        for (String each : clientManager.allClientId()) {
            Client client = clientManager.getClient(each);
            if (null == client || !client.isEphemeral()) {
                continue;
            }
            datum.add(client.generateSyncData());
        }
        ClientSyncDatumSnapshot snapshot = new ClientSyncDatumSnapshot();
        snapshot.setClientSyncDataList(datum);
        byte[] data = ApplicationUtils.getBean(Serializer.class).serialize(snapshot);
        return new DistroData(new DistroKey(DataOperation.SNAPSHOT.name(), TYPE), data);
    }
    
    // 返回本地需要校验的数据，发送给对等节点
    @Override
    public List<DistroData> getVerifyData() {
        List<DistroData> result = new LinkedList<>();
        for (String each : clientManager.allClientId()) {
            Client client = clientManager.getClient(each);
            if (null == client || !client.isEphemeral()) {
                continue;
            }
            if (clientManager.isResponsibleClient(client)) {
                // TODO add revision for client.
                // 仅仅返回clientId
                DistroClientVerifyInfo verifyData = new DistroClientVerifyInfo(client.getClientId(), 0);
                DistroKey distroKey = new DistroKey(client.getClientId(), TYPE);
                DistroData data = new DistroData(distroKey,
                        ApplicationUtils.getBean(Serializer.class).serialize(verifyData));
                data.setType(DataOperation.VERIFY);
                result.add(data);
            }
        }
        return result;
    }
}
