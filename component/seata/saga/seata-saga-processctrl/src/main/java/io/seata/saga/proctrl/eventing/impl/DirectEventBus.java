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
package io.seata.saga.proctrl.eventing.impl;

import java.util.List;
import java.util.Stack;

import io.seata.common.exception.FrameworkException;
import io.seata.common.util.CollectionUtils;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.proctrl.eventing.EventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地线程直接执行流程
 *
 * Deliver event to event consumer directly
 *
 * @author lorne.cl
 */
public class DirectEventBus extends AbstractEventBus<ProcessContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectEventBus.class);

    private static final String VAR_NAME_SYNC_EXE_STACK = "_sync_execution_stack_";

    @Override
    public boolean offer(ProcessContext context) throws FrameworkException {
        List<EventConsumer> eventHandlers = getEventConsumers(context.getClass());
        if (CollectionUtils.isEmpty(eventHandlers)) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("cannot find event handler by class: " + context.getClass());
            }
            return false;
        }

        // 初始化状态栈
        boolean isFirstEvent = false;
        Stack<ProcessContext> currentStack = (Stack<ProcessContext>)context.getVariable(VAR_NAME_SYNC_EXE_STACK);
        if (currentStack == null) {
            synchronized (context) {
                currentStack = (Stack<ProcessContext>)context.getVariable(VAR_NAME_SYNC_EXE_STACK);
                if (currentStack == null) {
                    currentStack = new Stack<>();
                    context.setVariable(VAR_NAME_SYNC_EXE_STACK, currentStack);
                    isFirstEvent = true;
                }
            }
        }

        // 流程上下文一层一层堆起来，由最初的在下面的while中循环执行，第二个到这时，第一个还在process中
        currentStack.push(context);

        // 第一个事务才允许执行
        if (isFirstEvent) {
            try {
                while (currentStack.size() > 0) {
                    ProcessContext currentContext = currentStack.pop();
                    for (EventConsumer eventHandler : eventHandlers) {
                        // 一层层处理
                        // 处理完是在路由完毕，路由时会往这个堆栈里放入状态
                        eventHandler.process(currentContext);
                    }
                }
            } finally {
                context.removeVariable(VAR_NAME_SYNC_EXE_STACK);
            }
        }
        return true;
    }
}