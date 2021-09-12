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
package io.seata.saga.engine.pcext.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.util.CollectionUtils;
import io.seata.common.util.StringUtils;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.utils.ExceptionUtils;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.State;
import io.seata.saga.statelang.domain.StateInstance;
import io.seata.saga.statelang.domain.StateMachine;
import io.seata.saga.statelang.domain.StateMachineInstance;
import io.seata.saga.statelang.domain.impl.AbstractTaskState;

/**
 * CompensationHolder
 *
 * @author lorne.cl
 */
public class CompensationHolder {

    /**
     * 存放需要进行补偿的状态
     *
     * states need compensation
     * key: stateName
     */
    private Map<String, StateInstance> statesNeedCompensation = new ConcurrentHashMap<>();

    /**
     * 存放补偿的状态
     *
     * states used to compensation
     * key: stateName
     */
    private Map<String, StateInstance> statesForCompensation = new ConcurrentHashMap<>();

    /**
     * 存放需要补偿的状态，作为一个堆栈供循环反向补偿
     *
     * stateStack need compensation
     */
    private Stack<StateInstance> stateStackNeedCompensation = new Stack<>();

    public static CompensationHolder getCurrent(ProcessContext context, boolean forceCreate) {

        CompensationHolder compensationholder = (CompensationHolder)context.getVariable(
            DomainConstants.VAR_NAME_CURRENT_COMPENSATION_HOLDER);
        if (compensationholder == null && forceCreate) {
            synchronized (context) {

                compensationholder = (CompensationHolder)context.getVariable(
                    DomainConstants.VAR_NAME_CURRENT_COMPENSATION_HOLDER);
                if (compensationholder == null) {
                    compensationholder = new CompensationHolder();
                    context.setVariable(DomainConstants.VAR_NAME_CURRENT_COMPENSATION_HOLDER, compensationholder);
                }
            }
        }
        return compensationholder;
    }

    public static List<StateInstance> findStateInstListToBeCompensated(ProcessContext context,
                                                                       List<StateInstance> stateInstanceList) {
        List<StateInstance> stateListToBeCompensated = null;
        // 获取所有状态列表
        if (CollectionUtils.isNotEmpty(stateInstanceList)) {
            stateListToBeCompensated = new ArrayList<>(stateInstanceList.size());

            StateMachine stateMachine = (StateMachine)context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE);
            StateMachineInstance stateMachineInstance = (StateMachineInstance)context.getVariable(
                DomainConstants.VAR_NAME_STATEMACHINE_INST);

            for (StateInstance stateInstance : stateInstanceList) {
                // 状态需要补偿，并且存在指定补偿状态，才添加到列表中
                if (stateNeedToCompensate(stateInstance)) {
                    State state = stateMachine.getState(EngineUtils.getOriginStateName(stateInstance));
                    AbstractTaskState taskState = null;
                    if (state instanceof AbstractTaskState) {
                        taskState = (AbstractTaskState)state;
                    }

                    //The data update service is not configured with the compensation state,
                    // The state machine needs to exit directly without compensation.
                    if (stateInstance.isForUpdate() /* 修改非查询的状态才要补偿 */ && taskState != null && StringUtils.isBlank(
                        taskState.getCompensateState())) {

                        String message = "StateMachineInstance[" + stateMachineInstance.getId() + ":" + stateMachine
                            .getName() + "] have a state [" + stateInstance.getName()
                            + "] is a service for update data, but no compensateState found.";
                        EngineExecutionException exception = ExceptionUtils.createEngineExecutionException(
                            FrameworkErrorCode.CompensationStateNotFound, message, stateMachineInstance, stateInstance);

                        // 失败，会再次补偿（回滚）
                        EngineUtils.failStateMachine(context, exception);

                        throw exception;
                    }

                    if (taskState != null && StringUtils.isNotBlank(taskState.getCompensateState())) {
                        stateListToBeCompensated.add(stateInstance);
                    }
                }
            }
        }
        return stateListToBeCompensated;
    }

    // 之前所有执行成功的状态都需要补偿（回滚）
    private static boolean stateNeedToCompensate(StateInstance stateInstance) {
        //If it has been retried, it will not be compensated
        if (stateInstance.isIgnoreStatus()) {
            return false;
        }

        // 子状态机状态 & 服务任务状态才需要回滚

        if (DomainConstants.STATE_TYPE_SUB_STATE_MACHINE.equals(stateInstance.getType())) {

            // 执行没失败的 & 补偿没成功的
            return (!ExecutionStatus.FA.equals(stateInstance.getStatus())) && (!ExecutionStatus.SU.equals(
                stateInstance.getCompensationStatus()));
        } else {

            // 比上面多了个判断当前状态不是补偿状态的
            // 你都正在补偿了，还补偿个锤子
            return DomainConstants.STATE_TYPE_SERVICE_TASK.equals(stateInstance.getType()) && !stateInstance
                .isForCompensation() && (!ExecutionStatus.FA.equals(stateInstance.getStatus())) && (!ExecutionStatus.SU
                .equals(stateInstance.getCompensationStatus()));
        }
    }

    public static void clearCurrent(ProcessContext context) {
        context.removeVariable(DomainConstants.VAR_NAME_CURRENT_COMPENSATION_HOLDER);
    }

    public Map<String, StateInstance> getStatesNeedCompensation() {
        return statesNeedCompensation;
    }

    public void addToBeCompensatedState(String stateName, StateInstance toBeCompensatedState) {
        this.statesNeedCompensation.put(stateName, toBeCompensatedState);
    }

    public Map<String, StateInstance> getStatesForCompensation() {
        return statesForCompensation;
    }

    public void addForCompensationState(String stateName, StateInstance forCompensationState) {
        this.statesForCompensation.put(stateName, forCompensationState);
    }

    public Stack<StateInstance> getStateStackNeedCompensation() {
        return stateStackNeedCompensation;
    }
}