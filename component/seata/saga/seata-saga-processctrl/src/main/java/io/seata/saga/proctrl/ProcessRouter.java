/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.saga.proctrl;

import io.seata.common.exception.FrameworkException;

/**
 * 路由当前状态到下一个状态，null或特殊的Success/End等指令表示结束
 *
 * Process Router
 *
 * @author jin.xie
 * @author lorne.cl
 */
public interface ProcessRouter {

    /**
     * route
     *
     * @param context
     * @return
     * @throws FrameworkException
     */
    Instruction route(ProcessContext context) throws FrameworkException;
}