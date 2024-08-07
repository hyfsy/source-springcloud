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
package io.seata.saga.engine.pcext.routers;

import java.util.Stack;

import io.seata.common.exception.FrameworkErrorCode;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.pcext.StateInstruction;
import io.seata.saga.engine.pcext.StateRouter;
import io.seata.saga.engine.pcext.utils.CompensationHolder;
import io.seata.saga.engine.pcext.utils.EngineUtils;
import io.seata.saga.engine.pcext.utils.LoopTaskUtils;
import io.seata.saga.proctrl.HierarchicalProcessContext;
import io.seata.saga.proctrl.Instruction;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.CompensateSubStateMachineState;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.State;
import io.seata.saga.statelang.domain.StateInstance;
import io.seata.saga.statelang.domain.StateMachine;
import io.seata.saga.statelang.domain.SubStateMachine;
import io.seata.saga.statelang.domain.impl.AbstractTaskState;
import io.seata.saga.statelang.domain.impl.LoopStartStateImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * TaskState Router
 *
 * @author lorne.cl
 */
public class TaskStateRouter implements StateRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStateRouter.class);

    @Override
    public Instruction route(ProcessContext context, State state) throws EngineExecutionException {

        // 获取当前执行完的指令
        StateInstruction stateInstruction = context.getInstruction(StateInstruction.class);
        // 状态机end了或fail了才会end
        if (stateInstruction.isEnd()) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(
                    "StateInstruction is ended, Stop the StateMachine executing. StateMachine[{}] Current State[{}]",
                    stateInstruction.getStateMachineName(), state.getName());
            }
            return null;
        }

        // loop中，不能给下个指令
        // check if in loop async condition
        if (Boolean.TRUE.equals(context.getVariable(DomainConstants.VAR_NAME_IS_LOOP_STATE))) {
            return null;
        }

        // 正在触发补偿状态中，获取下一个需要补偿的状态，补偿的状态不支持Next
        //The current CompensationTriggerState can mark the compensation process is started and perform compensation
        // route processing.
        State compensationTriggerState = (State)context.getVariable(
            DomainConstants.VAR_NAME_CURRENT_COMPEN_TRIGGER_STATE);
        if (compensationTriggerState != null) {
            return compensateRoute(context, compensationTriggerState);
        }

        // 异常的Next路由
        //There is an exception route, indicating that an exception is thrown, and the exception route is prioritized.
        String next = (String)context.getVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE);

        if (StringUtils.hasLength(next)) {
            context.removeVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION_ROUTE);
        } else {
            next = state.getNext();
        }

        // Choice路由
        //If next is empty, the state selected by the Choice state was taken.
        if (!StringUtils.hasLength(next) && context.hasVariable(DomainConstants.VAR_NAME_CURRENT_CHOICE)) {
            next = (String)context.getVariable(DomainConstants.VAR_NAME_CURRENT_CHOICE);
            context.removeVariable(DomainConstants.VAR_NAME_CURRENT_CHOICE);
        }

        // 没有就结束状态机
        if (!StringUtils.hasLength(next)) {
            return null;
        }

        StateMachine stateMachine = state.getStateMachine();

        // 状态机中找下一个状态，找不到抛异常
        State nextState = stateMachine.getState(next);
        if (nextState == null) {
            throw new EngineExecutionException("Next state[" + next + "] is not exits",
                FrameworkErrorCode.ObjectNotExists);
        }

        stateInstruction.setStateName(next);

        // 下个状态为loop状态，设变设置下临时状态
        if (null != LoopTaskUtils.getLoopConfig(context, nextState)) {
            stateInstruction.setTemporaryState(new LoopStartStateImpl());
        }

        return stateInstruction;
    }

    private Instruction compensateRoute(ProcessContext context, State compensationTriggerState) {

        //If there is already a compensation state that has been executed,
        // it is judged whether it is wrong or unsuccessful,
        // and the compensation process is interrupted.
        // 校验前一个补偿的状态是否出错了，出错了需要立即终止状态机
        if (Boolean.TRUE.equals(context.getVariable(DomainConstants.VAR_NAME_FIRST_COMPENSATION_STATE_STARTED))) {

            Exception exception = (Exception)context.getVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION);
            if (exception != null) {
                EngineUtils.endStateMachine(context);
                return null;
            }

            StateInstance stateInstance = (StateInstance)context.getVariable(DomainConstants.VAR_NAME_STATE_INST);
            if (stateInstance != null && (!ExecutionStatus.SU.equals(stateInstance.getStatus()))) {
                EngineUtils.endStateMachine(context);
                return null;
            }
        }

        // 获取需要补偿的状态
        Stack<StateInstance> stateStackToBeCompensated = CompensationHolder.getCurrent(context, true)
            .getStateStackNeedCompensation();
        if (!stateStackToBeCompensated.isEmpty()) {

            // 一次执行一个状态，执行完会再次路由到这
            StateInstance stateToBeCompensated = stateStackToBeCompensated.pop();

            StateMachine stateMachine = (StateMachine)context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE);
            State state = stateMachine.getState(EngineUtils.getOriginStateName(stateToBeCompensated));
            if (state != null && state instanceof AbstractTaskState) {

                AbstractTaskState taskState = (AbstractTaskState)state;

                StateInstruction instruction = context.getInstruction(StateInstruction.class);

                // 获取补偿的状态
                State compensateState = null;
                String compensateStateName = taskState.getCompensateState();
                if (StringUtils.hasLength(compensateStateName)) {
                    compensateState = stateMachine.getState(compensateStateName);
                }

                // 子状态机的情况，找默认生成的补偿状态
                if (compensateState == null && (taskState instanceof SubStateMachine)) {
                    compensateState = ((SubStateMachine)taskState).getCompensateStateObject();
                    instruction.setTemporaryState(compensateState);
                }

                // 没有补偿，结束状态机
                if (compensateState == null) {
                    EngineUtils.endStateMachine(context);
                    return null;
                }

                instruction.setStateName(compensateState.getName());

                // 添加将进行补偿的状态
                CompensationHolder.getCurrent(context, true).addToBeCompensatedState(compensateState.getName(),
                    stateToBeCompensated);

                ((HierarchicalProcessContext)context).setVariableLocally(
                    DomainConstants.VAR_NAME_FIRST_COMPENSATION_STATE_STARTED, true);

                if (compensateState instanceof CompensateSubStateMachineState) {
                    ((HierarchicalProcessContext)context).setVariableLocally(
                        compensateState.getName() + DomainConstants.VAR_NAME_SUB_MACHINE_PARENT_ID,
                        EngineUtils.generateParentId(stateToBeCompensated));
                }

                return instruction;
            }
        }

        // 所有状态都补偿完毕
        context.removeVariable(DomainConstants.VAR_NAME_CURRENT_COMPEN_TRIGGER_STATE);

        // 获取补偿触发器的下一个状态，没有就结束
        String compensationTriggerStateNext = compensationTriggerState.getNext();
        if (StringUtils.isEmpty(compensationTriggerStateNext)) {
            EngineUtils.endStateMachine(context);
            return null;
        }

        StateInstruction instruction = context.getInstruction(StateInstruction.class);
        instruction.setStateName(compensationTriggerStateNext);
        return instruction;
    }

}