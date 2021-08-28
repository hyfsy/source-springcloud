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

package com.alibaba.nacos.core.distributed.distro.task.execute;

import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.component.DistroComponentHolder;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.utils.Loggers;

/**
 * Distro sync change task.
 *
 * @author xiweng.yy
 */
public class DistroSyncChangeTask extends AbstractDistroExecuteTask {
    
    private static final DataOperation OPERATION = DataOperation.CHANGE;
    
    public DistroSyncChangeTask(DistroKey distroKey, DistroComponentHolder distroComponentHolder) {
        super(distroKey, distroComponentHolder);
    }
    
    @Override
    protected DataOperation getDataOperation() {
        return OPERATION;
    }
    
    @Override
    protected boolean doExecute() {
        String type = getDistroKey().getResourceType();
        DistroData distroData = getDistroData(type);
        if (null == distroData) {
            Loggers.DISTRO.warn("[DISTRO] {} with null data to sync, skip", toString());
            return true;
        }
        // 通过传输代理发起http请求
        return getDistroComponentHolder().findTransportAgent(type)
                .syncData(distroData, getDistroKey().getTargetServer());
    }
    
    @Override
    protected void doExecuteWithCallback(DistroCallback callback) {
        String type = getDistroKey().getResourceType();
        DistroData distroData = getDistroData(type);
        if (null == distroData) {
            Loggers.DISTRO.warn("[DISTRO] {} with null data to sync, skip", toString());
            return;
        }
        // 通过传输代理发起http请求
        getDistroComponentHolder().findTransportAgent(type)
                .syncData(distroData, getDistroKey().getTargetServer(), callback);
    }
    
    @Override
    public String toString() {
        return "DistroSyncChangeTask for " + getDistroKey().toString();
    }
    
    // 通过distro key获取对应的数据
    private DistroData getDistroData(String type) {
        DistroData result = getDistroComponentHolder().findDataStorage(type).getDistroData(getDistroKey());
        if (null != result) {
            result.setType(OPERATION);
        }
        return result;
    }
}