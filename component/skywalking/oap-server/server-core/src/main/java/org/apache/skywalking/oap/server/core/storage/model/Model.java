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

package org.apache.skywalking.oap.server.core.storage.model;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;

/**
 * 表模型
 *
 * The model definition of a logic entity.
 */
@Getter
@EqualsAndHashCode
public class Model {
    // 模块名称
    private final String name;
    // 模块实体的列
    private final List<ModelColumn> columns;
    // 模块实体支持的查询条件
    private final List<ExtraQueryIndex> extraQueryIndices;
    private final int scopeId;
    // 缩减采样的单位
    private final DownSampling downsampling;
    // 是否为record数据，或metrics数据
    private final boolean record;
    private final boolean superDataset;
    // 时间连续，即是否缩减采样
    private final boolean isTimeSeries;
    private final String aggregationFunctionName;

    public Model(final String name,
                 final List<ModelColumn> columns,
                 final List<ExtraQueryIndex> extraQueryIndices,
                 final int scopeId,
                 final DownSampling downsampling,
                 final boolean record,
                 final boolean superDataset,
                 final String aggregationFunctionName) {
        this.name = name;
        this.columns = columns;
        this.extraQueryIndices = extraQueryIndices;
        this.scopeId = scopeId;
        this.downsampling = downsampling;
        // 非 none 都是
        this.isTimeSeries = !DownSampling.None.equals(downsampling);
        this.record = record;
        this.superDataset = superDataset;
        this.aggregationFunctionName = aggregationFunctionName;
    }
}
