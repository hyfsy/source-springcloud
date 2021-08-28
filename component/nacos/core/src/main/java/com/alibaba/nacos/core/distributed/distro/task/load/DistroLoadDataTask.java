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

package com.alibaba.nacos.core.distributed.distro.task.load;

import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.distributed.distro.DistroConfig;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.component.DistroDataProcessor;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.utils.GlobalExecutor;
import com.alibaba.nacos.core.utils.Loggers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 启动时的数据全量加载任务
 *
 * Distro load data task.
 *
 * @author xiweng.yy
 */
public class DistroLoadDataTask implements Runnable {
    
    // 获取集群其他节点
    private final ServerMemberManager memberManager;
    
    // 获取各种组件
    private final DistroComponentHolder distroComponentHolder;
    
    private final DistroConfig distroConfig;
    
    private final DistroCallback loadCallback;
    
    // 加载任务的完成状态 resourceType -> isSuccess
    private final Map<String, Boolean> loadCompletedMap;
    
    public DistroLoadDataTask(ServerMemberManager memberManager, DistroComponentHolder distroComponentHolder,
            DistroConfig distroConfig, DistroCallback loadCallback) {
        this.memberManager = memberManager;
        this.distroComponentHolder = distroComponentHolder;
        this.distroConfig = distroConfig;
        this.loadCallback = loadCallback;
        loadCompletedMap = new HashMap<>(1);
    }
    
    @Override
    public void run() {
        try {
            // 全量同步数据
            load();
            // 数据没有全部都加载失败，再次提交当前任务
            if (!checkCompleted()) {
                GlobalExecutor.submitLoadDataTask(this, distroConfig.getLoadDataRetryDelayMillis());
            } else {
                // 通知DistroProtocol初始化完毕
                loadCallback.onSuccess();
                Loggers.DISTRO.info("[DISTRO-INIT] load snapshot data success");
            }
        } catch (Exception e) {
            loadCallback.onFailed(e);
            Loggers.DISTRO.error("[DISTRO-INIT] load snapshot data failed. ", e);
        }
    }
    
    private void load() throws Exception {
        while (memberManager.allMembersWithoutSelf().isEmpty()) {
            Loggers.DISTRO.info("[DISTRO-INIT] waiting server list init...");
            TimeUnit.SECONDS.sleep(1);
        }
        while (distroComponentHolder.getDataStorageTypes().isEmpty()) {
            Loggers.DISTRO.info("[DISTRO-INIT] waiting distro data storage register...");
            TimeUnit.SECONDS.sleep(1);
        }
        // 每种数据存储都进行远程同步
        for (String each : distroComponentHolder.getDataStorageTypes()) {
            if (!loadCompletedMap.containsKey(each) || !loadCompletedMap.get(each)) {
                // 添加是否加载完毕
                loadCompletedMap.put(each, loadAllDataSnapshotFromRemote(each));
            }
        }
    }
    
    private boolean loadAllDataSnapshotFromRemote(String resourceType) {
        // 用于去其他节点请求数据
        DistroTransportAgent transportAgent = distroComponentHolder.findTransportAgent(resourceType);
        // 处理返回的同步数据
        DistroDataProcessor dataProcessor = distroComponentHolder.findDataProcessor(resourceType);
        if (null == transportAgent || null == dataProcessor) {
            Loggers.DISTRO.warn("[DISTRO-INIT] Can't find component for type {}, transportAgent: {}, dataProcessor: {}",
                    resourceType, transportAgent, dataProcessor);
            return false;
        }
        // 从所有节点中拉取数据，一个返回就算成功
        for (Member each : memberManager.allMembersWithoutSelf()) {
            try {
                Loggers.DISTRO.info("[DISTRO-INIT] load snapshot {} from {}", resourceType, each.getAddress());
                // 请求同步数据
                DistroData distroData = transportAgent.getDatumSnapshot(each.getAddress());
                // 处理同步数据
                boolean result = dataProcessor.processSnapshot(distroData);
                Loggers.DISTRO
                        .info("[DISTRO-INIT] load snapshot {} from {} result: {}", resourceType, each.getAddress(),
                                result);
                // 存储初始化完毕
                if (result) {
                    distroComponentHolder.findDataStorage(resourceType).finishInitial();
                    // 从一个节点拉取到就同步结束
                    return true;
                }
            } catch (Exception e) {
                Loggers.DISTRO.error("[DISTRO-INIT] load snapshot {} from {} failed.", resourceType, each.getAddress(), e);
            }
        }
        return false;
    }
    
    private boolean checkCompleted() {
        if (distroComponentHolder.getDataStorageTypes().size() != loadCompletedMap.size()) {
            return false;
        }
        for (Boolean each : loadCompletedMap.values()) {
            if (!each) {
                return false;
            }
        }
        return true;
    }
}
