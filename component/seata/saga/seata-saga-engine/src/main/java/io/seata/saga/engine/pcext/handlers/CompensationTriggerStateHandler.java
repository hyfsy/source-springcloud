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

import java.util.List;
import java.util.Stack;

import io.seata.common.util.CollectionUtils;
import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.pcext.StateHandler;
import io.seata.saga.engine.pcext.StateInstruction;
import io.seata.saga.engine.pcext.utils.CompensationHolder;
import io.seata.saga.engine.pcext.utils.EngineUtils;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.StateInstance;
import io.seata.saga.statelang.domain.StateMachineInstance;

/**
 * CompensationTriggerState Handler
 * Start to execute compensation
 *
 * @author lorne.cl
 */
public class CompensationTriggerStateHandler implements StateHandler {

    @Override
    public void process(ProcessContext context) throws EngineExecutionException {

        StateInstruction instruction = context.getInstruction(StateInstruction.class);

        StateMachineInstance stateMachineInstance = (StateMachineInstance)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_INST);
        StateMachineConfig stateMachineConfig = (StateMachineConfig)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_CONFIG);
        List<StateInstance> stateInstanceList = stateMachineInstance.getStateList();
        if (CollectionUtils.isEmpty(stateInstanceList)) {
            stateInstanceList = stateMachineConfig.getStateLogStore().queryStateInstanceListByMachineInstanceId(
                stateMachineInstance.getId());
        }

        // 通过状态列表，获取需要补偿的状态（之前执行成功的）
        List<StateInstance> stateListToBeCompensated = CompensationHolder.findStateInstListToBeCompensated(context,
            stateInstanceList);
        if (CollectionUtils.isNotEmpty(stateListToBeCompensated)) {
            //Clear exceptions that occur during forward execution
            Exception e = (Exception)context.removeVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION);
            if (e != null) {
                stateMachineInstance.setException(e);
            }

            Stack<StateInstance> stateStackToBeCompensated = CompensationHolder.getCurrent(context, true)
                .getStateStackNeedCompensation();
            stateStackToBeCompensated.addAll(stateListToBeCompensated);

            //If the forward running state is empty or running,
            // it indicates that the compensation state is automatically initiated in the state machine,
            // and the forward state needs to be changed to the UN state.
            //If the forward status is not the two states, then the compensation operation should be initiated by
            // server recovery,
            // and the forward state should not be modified.
            // 这种情况说明状态机还没有被恢复，此处需要初始化状态机的状态，标记回滚的未知情况
            if (stateMachineInstance.getStatus() == null || ExecutionStatus.RU.equals(
                stateMachineInstance.getStatus())) {
                stateMachineInstance.setStatus(ExecutionStatus.UN);
            }
            //Record the status of the state machine as "compensating", and the subsequent routing logic will route
            // to the compensation state
            // process执行完毕，接下来由路由去到补偿状态
            stateMachineInstance.setCompensationStatus(ExecutionStatus.RU);
            // 上下文添加补偿触发的状态
            context.setVariable(DomainConstants.VAR_NAME_CURRENT_COMPEN_TRIGGER_STATE, instruction.getState(context));
        }
        // 没有补偿状态的，直接结束状态机
        else {
            EngineUtils.endStateMachine(context);
        }
    }
}