/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.storage.ttl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.cluster.ClusterModule;
import org.apache.skywalking.oap.server.core.cluster.ClusterNodesQuery;
import org.apache.skywalking.oap.server.core.cluster.RemoteInstance;
import org.apache.skywalking.oap.server.core.storage.IHistoryDeleteDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.IModelManager;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * TTL = Time To Live
 *
 * DataTTLKeeperTimer is an internal timer, it drives the {@link IHistoryDeleteDAO} to remove the expired data. TTL
 * configurations are provided in {@link CoreModuleConfig}, some storage implementations, such as ES6/ES7, provides an
 * override TTL, which could be more suitable for the implementation. No matter which TTL configurations are set, they
 * are all driven by this timer.
 */
@Slf4j
public enum DataTTLKeeperTimer {
    INSTANCE;

    private ModuleManager moduleManager;
    private ClusterNodesQuery clusterNodesQuery;
    private CoreModuleConfig moduleConfig;

    public void start(ModuleManager moduleManager, CoreModuleConfig moduleConfig) {
        this.moduleManager = moduleManager;
        this.clusterNodesQuery = moduleManager.find(ClusterModule.NAME).provider().getService(ClusterNodesQuery.class);
        this.moduleConfig = moduleConfig;

        Executors.newSingleThreadScheduledExecutor()
                 .scheduleAtFixedRate(
                     new RunnableWithExceptionProtection(
                         this::delete,
                         t -> log.error("Remove data in background failure.", t)
                     ), moduleConfig
                         .getDataKeeperExecutePeriod(), moduleConfig.getDataKeeperExecutePeriod(), TimeUnit.MINUTES);
    }

    /**
     * DataTTLKeeperTimer starts in every OAP node, but the deletion only work when it is as the first node in the OAP
     * node list from {@link ClusterNodesQuery}.
     */
    private void delete() {
        // TODO 只清理第一个节点是self的情况？？？
        List<RemoteInstance> remoteInstances = clusterNodesQuery.queryRemoteNodes();
        if (CollectionUtils.isNotEmpty(remoteInstances) && !remoteInstances.get(0).getAddress().isSelf()) {
            log.info("The selected first getAddress is {}. Skip.", remoteInstances.get(0).toString());
            return;
        }

        // 对每个模块执行清理操作
        log.info("Beginning to remove expired metrics from the storage.");
        IModelManager modelGetter = moduleManager.find(CoreModule.NAME).provider().getService(IModelManager.class);
        List<Model> models = modelGetter.allModels();
        models.forEach(this::execute);
    }

    private void execute(Model model) {
        try {
            // 模块是否为限制采样
            if (!model.isTimeSeries()) {
                return;
            }

            // 通过IHistoryDeleteDAO，根据实体不同的ttl删除历史数据
            moduleManager.find(StorageModule.NAME)
                         .provider()
                         .getService(IHistoryDeleteDAO.class)
                         .deleteHistory(model, Metrics.TIME_BUCKET,
                                        model.isRecord() ? moduleConfig.getRecordDataTTL() : moduleConfig.getMetricsDataTTL()
                         );
        } catch (IOException e) {
            log.warn("History of {} delete failure", model.getName());
            log.error(e.getMessage(), e);
        }
    }
}
