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

package org.apache.skywalking.oap.server.core.remote.client;

import org.apache.skywalking.oap.server.core.remote.data.StreamData;

public interface RemoteClient extends Comparable<RemoteClient> {

    Address getAddress();

    void connect();

    void close();

    /**
     * 推送数据给当前客户端节点
     *
     * @param nextWorkerName worker名称，可通过worker处理该数据
     * @param streamData 流数据
     */
    void push(String nextWorkerName, StreamData streamData);
}
