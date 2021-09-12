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
package io.seata.saga.engine.pcext.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.util.StringUtils;
import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.pcext.StateHandler;
import io.seata.saga.engine.pcext.StateInstruction;
import io.seata.saga.engine.pcext.utils.EngineUtils;
import io.seata.saga.engine.pcext.utils.LoopContextHolder;
import io.seata.saga.engine.pcext.utils.LoopTaskUtils;
import io.seata.saga.proctrl.HierarchicalProcessContext;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.proctrl.impl.ProcessContextImpl;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.StateMachineInstance;
import io.seata.saga.statelang.domain.TaskState.Loop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loop State Handler
 * Start Loop Execution
 *
 * @author anselleeyy
 */
public class LoopStartStateHandler implements StateHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoopStartStateHandler.class);
    private static final int AWAIT_TIMEOUT = 1000;

    @Override
    public void process(ProcessContext context) throws EngineExecutionException {

        StateInstruction instruction = context.getInstruction(StateInstruction.class);
        StateMachineInstance stateMachineInstance = (StateMachineInstance)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_INST);
        StateMachineConfig stateMachineConfig = (StateMachineConfig)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_CONFIG);

        // 立马置空临时状态
        instruction.setTemporaryState(null);

        Loop loop = LoopTaskUtils.getLoopConfig(context, instruction.getState(context));
        LoopContextHolder loopContextHolder = LoopContextHolder.getCurrent(context, true);
        Semaphore semaphore = null;
        int maxInstances = 0;
        List<ProcessContext> loopContextList = new ArrayList<>();

        if (null != loop) {

            // loop需要异步支持
            if (!stateMachineConfig.isEnableAsync() || null == stateMachineConfig.getAsyncProcessCtrlEventPublisher()) {
                throw new EngineExecutionException(
                    "Asynchronous start is disabled. Loop execution will run asynchronous, please set "
                        + "StateMachineConfig.enableAsync=true first.", FrameworkErrorCode.AsynchronousStartDisabled);
            }

            int totalInstances;
            // forward会通过之前失败的和未开始的来重新加载
            if (DomainConstants.OPERATION_NAME_FORWARD.equals(context.getVariable(DomainConstants.VAR_NAME_OPERATION_NAME))) {
                LoopTaskUtils.reloadLoopContext(context, instruction.getState(context).getName());
                totalInstances = loopContextHolder.getNrOfInstances().get() - loopContextHolder.getNrOfCompletedInstances().get();
            }
            // 创建一个上下文
            else {
                LoopTaskUtils.createLoopCounterContext(context);
                totalInstances = loopContextHolder.getNrOfInstances().get();
            }
            // 并行数
            maxInstances = Math.min(loop.getParallel(), totalInstances);
            semaphore = new Semaphore(maxInstances);
            context.setVariable(DomainConstants.LOOP_SEMAPHORE, semaphore);
            context.setVariable(DomainConstants.VAR_NAME_IS_LOOP_STATE, true);

            // publish loop tasks
            for (int i = 0; i < totalInstances; i++) {
                try {
                    semaphore.acquire(); // 加锁，保证并行度

                    ProcessContextImpl tempContext;
                    // fail end inst should be forward without completion condition check
                    if (!loopContextHolder.getForwardCounterStack().isEmpty()) {
                        // 失败的loopCounter
                        int failEndLoopCounter = loopContextHolder.getForwardCounterStack().pop();
                        tempContext = (ProcessContextImpl)LoopTaskUtils.createLoopEventContext(context, failEndLoopCounter);
                    }
                    // loop已经失败或条件满足，结束
                    else if (loopContextHolder.isFailEnd() || LoopTaskUtils.isCompletionConditionSatisfied(context)) {
                        semaphore.release();
                        break;
                    }
                    // 新创建
                    else {
                        tempContext = (ProcessContextImpl)LoopTaskUtils.createLoopEventContext(context, -1);
                    }

                    // 子状态机需要设置下是否为forward状态
                    if (DomainConstants.OPERATION_NAME_FORWARD.equals(context.getVariable(DomainConstants.VAR_NAME_OPERATION_NAME))) {
                        ((HierarchicalProcessContext)context).setVariableLocally(
                            DomainConstants.VAR_NAME_IS_FOR_SUB_STATMACHINE_FORWARD, LoopTaskUtils.isForSubStateMachineForward(tempContext));
                    }
                    stateMachineConfig.getAsyncProcessCtrlEventPublisher().publish(tempContext);
                    loopContextHolder.getNrOfActiveInstances().incrementAndGet();
                    loopContextList.add(tempContext);
                } catch (InterruptedException e) {
                    LOGGER.error("try execute loop task for State: [{}] is interrupted, message: [{}]",
                        instruction.getStateName(), e.getMessage());
                    throw new EngineExecutionException(e);
                }
            }
        } else {
            LOGGER.warn("Loop config of State [{}] is illegal, will execute as normal", instruction.getStateName());
            instruction.setTemporaryState(instruction.getState(context));
        }

        try {
            if (null != semaphore) {
                boolean isFinished = false;
                while (!isFinished) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("wait {}ms for loop state [{}] finish", AWAIT_TIMEOUT, instruction.getStateName());
                    }
                    // 等待loop执行的任务结束，这边一下要拿所有的票据，所以会在此处死循环
                    isFinished = semaphore.tryAcquire(maxInstances, AWAIT_TIMEOUT, TimeUnit.MILLISECONDS);
                }

                if (loopContextList.size() > 0) {
                    LoopTaskUtils.putContextToParent(context, loopContextList, instruction.getState(context));
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("State: [{}] wait loop execution complete is interrupted, message: [{}]",
                instruction.getStateName(), e.getMessage());
            throw new EngineExecutionException(e);
        } finally {
            context.removeVariable(DomainConstants.LOOP_SEMAPHORE);
            context.removeVariable(DomainConstants.VAR_NAME_IS_LOOP_STATE);
            LoopContextHolder.clearCurrent(context);
        }

        if (loopContextHolder.isFailEnd()) {
            // 获取异常匹配的路由
            String currentExceptionRoute = LoopTaskUtils.decideCurrentExceptionRoute(loopContextList, stateMachineInstance.getStateMachine());
            // 有就设置为当前的路由
            if (StringUtils.isNotBlank(currentExceptionRoute)) {
                ((HierarchicalProcessContext)context).setVariableLocally(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE, currentExceptionRoute);
            }
            else {
                // 有异常就抛，没有就继续执行剩下的状态
                for (ProcessContext processContext : loopContextList) {
                    if (processContext.hasVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION)) {
                        Exception exception = (Exception)processContext.getVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION);
                        EngineUtils.failStateMachine(context, exception);
                        break;
                    }
                }
            }
        }

    }
}
