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
package io.seata.saga.engine.strategy.impl;

import java.util.List;

import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.util.CollectionUtils;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.pcext.utils.CompensationHolder;
import io.seata.saga.engine.strategy.StatusDecisionStrategy;
import io.seata.saga.engine.utils.ExceptionUtils;
import io.seata.saga.engine.utils.ExceptionUtils.NetExceptionType;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.StateInstance;
import io.seata.saga.statelang.domain.StateMachineInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 决定状态机的最终状态，为全局提交做准备
 *
 * Default state machine execution status decision strategy
 *
 * @author lorne.cl
 * @see StatusDecisionStrategy
 */
public class DefaultStatusDecisionStrategy implements StatusDecisionStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStatusDecisionStrategy.class);

    /**
     * decide machine compensate status
     *
     * @param stateMachineInstance
     * @param compensationHolder
     */
    public static void decideMachineCompensateStatus(StateMachineInstance stateMachineInstance,
                                                     CompensationHolder compensationHolder) {
        // 标记下原本的状态为UN
        if (stateMachineInstance.getStatus() == null || ExecutionStatus.RU.equals(stateMachineInstance.getStatus())) {

            stateMachineInstance.setStatus(ExecutionStatus.UN);
        }
        // 有没完成的补偿状态
        if (!compensationHolder.getStateStackNeedCompensation().isEmpty()) {

            boolean hasCompensateSUorUN = false;
            for (StateInstance forCompensateState : compensationHolder.getStatesForCompensation().values()) {
                // 查询是否有成功或UN的状态
                if (ExecutionStatus.UN.equals(forCompensateState.getStatus()) || ExecutionStatus.SU.equals(
                    forCompensateState.getStatus())) {
                    hasCompensateSUorUN = true;
                    break;
                }
            }
            if (hasCompensateSUorUN) {
                stateMachineInstance.setCompensationStatus(ExecutionStatus.UN);
            }
            // 没有就失败
            else {
                stateMachineInstance.setCompensationStatus(ExecutionStatus.FA);
            }
        } else {

            // 查询是否有失败的状态
            boolean hasCompensateError = false;
            for (StateInstance forCompensateState : compensationHolder.getStatesForCompensation().values()) {
                if (!ExecutionStatus.SU.equals(forCompensateState.getStatus())) {
                    hasCompensateError = true;
                    break;
                }

            }
            // 标记
            if (hasCompensateError) {
                stateMachineInstance.setCompensationStatus(ExecutionStatus.UN);
            } else {
                stateMachineInstance.setCompensationStatus(ExecutionStatus.SU);
            }
        }
    }

    /**
     * set machine status based on state list
     *
     * @param stateMachineInstance
     * @param stateList
     * @return
     */
    public static void setMachineStatusBasedOnStateListAndException(StateMachineInstance stateMachineInstance,
                                                                    List<StateInstance> stateList, Exception exp) {
        // 状态机状态被改变
        boolean hasSetStatus = false;
        // ServiceTask成功完成的状态，更新非补偿
        boolean hasSuccessUpdateService = false;
        // 有处理的流程状态
        if (CollectionUtils.isNotEmpty(stateList)) {
            boolean hasUnsuccessService = false;

            for (int i = stateList.size() - 1; i >= 0; i--) {
                StateInstance stateInstance = stateList.get(i);

                // 忽略 | 当前状态是补偿状态
                if (stateInstance.isIgnoreStatus() || stateInstance.isForCompensation()) {
                    continue;
                }

                // 未知状态，标记状态机状态为未知
                if (ExecutionStatus.UN.equals(stateInstance.getStatus())) {
                    stateMachineInstance.setStatus(ExecutionStatus.UN); // 有失败的状态，不能设置成功
                    hasSetStatus = true;
                }
                // 成功状态，校验ServiceTask是否为更新非补偿
                else if (ExecutionStatus.SU.equals(stateInstance.getStatus())) {
                    if (DomainConstants.STATE_TYPE_SERVICE_TASK.equals(stateInstance.getType())) {
                        if (stateInstance.isForUpdate() && !stateInstance.isForCompensation()) {
                            hasSuccessUpdateService = true;
                        }
                    }
                } else if (ExecutionStatus.SK.equals(stateInstance.getStatus())) {
                    // ignore
                }
                // 失败状态
                else {
                    hasUnsuccessService = true;
                }
            }

            // 没有设置过状态 & 有失败的状态
            if (!hasSetStatus && hasUnsuccessService) {
                // 又有失败又有成功，设置未知
                if (hasSuccessUpdateService) {
                    stateMachineInstance.setStatus(ExecutionStatus.UN);
                }
                // 都失败，设为失败
                else {
                    stateMachineInstance.setStatus(ExecutionStatus.FA);
                }
                // 标记设置过状态
                hasSetStatus = true;
            }
        }

        // 没有设置过状态，通过异常再次尝试设置
        if (!hasSetStatus) {
            setMachineStatusBasedOnException(stateMachineInstance, exp, hasSuccessUpdateService);
        }
    }

    /**
     * set machine status based on net exception
     *
     * @param stateMachineInstance
     * @param exp
     */
    public static void setMachineStatusBasedOnException(StateMachineInstance stateMachineInstance, Exception exp,
                                                        boolean hasSuccessUpdateService) {
        // 异常没有，标记状态机成功
        if (exp == null) {
            stateMachineInstance.setStatus(ExecutionStatus.SU);
        }
        // 执行异常，标记未知
        else if (exp instanceof EngineExecutionException
                && FrameworkErrorCode.StateMachineExecutionTimeout.equals(((EngineExecutionException)exp).getErrcode())) {
            stateMachineInstance.setStatus(ExecutionStatus.UN);
        }
        // 有成功的状态，标记未知
        else if (hasSuccessUpdateService) {
            stateMachineInstance.setStatus(ExecutionStatus.UN);
        } else {
            NetExceptionType t = ExceptionUtils.getNetExceptionType(exp);
            if (t != null) {
                // 网络连接异常、非网络异常，失败
                if (t.equals(NetExceptionType.CONNECT_EXCEPTION) || t.equals(NetExceptionType.CONNECT_TIMEOUT_EXCEPTION)
                    || t.equals(NetExceptionType.NOT_NET_EXCEPTION)) {
                    stateMachineInstance.setStatus(ExecutionStatus.FA);
                }
                // 读取超时异常，位置
                else if (t.equals(NetExceptionType.READ_TIMEOUT_EXCEPTION)) {
                    stateMachineInstance.setStatus(ExecutionStatus.UN);
                }
            }
            // 好像没有这种情况
            else {
                stateMachineInstance.setStatus(ExecutionStatus.UN);
            }
        }
    }

    @Override
    public void decideOnEndState(ProcessContext context, StateMachineInstance stateMachineInstance, Exception exp) {

        // 补偿状态下的全局结束
        if (ExecutionStatus.RU.equals(stateMachineInstance.getCompensationStatus())) {

            CompensationHolder compensationHolder = CompensationHolder.getCurrent(context, true);
            decideMachineCompensateStatus(stateMachineInstance, compensationHolder);
        }
        // FailEndState 状态进来的
        else {
            Object failEndStateFlag = context.getVariable(DomainConstants.VAR_NAME_FAIL_END_STATE_FLAG);
            boolean isComeFromFailEndState = failEndStateFlag != null && (Boolean)failEndStateFlag;

            // 设置状态机的前进状态
            decideMachineForwardExecutionStatus(stateMachineInstance, exp, isComeFromFailEndState);
        }

        // 状态机完成了，补偿状态直接置为失败表示结束
        if (stateMachineInstance.getCompensationStatus() != null && DomainConstants.OPERATION_NAME_FORWARD.equals(
            context.getVariable(DomainConstants.VAR_NAME_OPERATION_NAME)) && ExecutionStatus.SU.equals(
            stateMachineInstance.getStatus())) {

            stateMachineInstance.setCompensationStatus(ExecutionStatus.FA);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "StateMachine Instance[id:{},name:{}] execute finish with status[{}], compensation status [{}].",
                stateMachineInstance.getId(), stateMachineInstance.getStateMachine().getName(),
                stateMachineInstance.getStatus(), stateMachineInstance.getCompensationStatus());
        }
    }

    @Override
    public void decideOnTaskStateFail(ProcessContext context, StateMachineInstance stateMachineInstance,
                                      Exception exp) {
        // 没决定好，就给未知
        if (!decideMachineForwardExecutionStatus(stateMachineInstance, exp, true)) {

            stateMachineInstance.setCompensationStatus(ExecutionStatus.UN);
        }
    }

    /**
     * Determine the forward execution state of the state machine
     *
     * @param stateMachineInstance
     * @param exp
     * @param specialPolicy
     * @return
     */
    @Override
    public boolean decideMachineForwardExecutionStatus(StateMachineInstance stateMachineInstance, Exception exp,
                                                       boolean specialPolicy) {
        boolean result = false;

        // 状态机没设置过状态
        if (stateMachineInstance.getStatus() == null || ExecutionStatus.RU.equals(stateMachineInstance.getStatus())) {
            result = true;

            List<StateInstance> stateList = stateMachineInstance.getStateList();

            // 首先更新下状态机的最新状态
            setMachineStatusBasedOnStateListAndException(stateMachineInstance, stateList, exp);

            // 失败了，或来自FailEndState的
            // 有状态为 更新 | 补偿 的，更新状态机为未知状态，表示不能算完成，需要继续前进
            if (specialPolicy && ExecutionStatus.SU.equals(stateMachineInstance.getStatus())) {
                for (StateInstance stateInstance : stateMachineInstance.getStateList()) {
                    if (!stateInstance.isIgnoreStatus() && (stateInstance.isForUpdate() || stateInstance
                        .isForCompensation())) {
                        stateMachineInstance.setStatus(ExecutionStatus.UN);
                        break;
                    }
                }
                // 还是成功状态，说明什么？这边是失败的状态过来的，状态机的状态不能为SUCCEED，因为没有要补偿的，直接指定状态机失败
                if (ExecutionStatus.SU.equals(stateMachineInstance.getStatus())) {
                    stateMachineInstance.setStatus(ExecutionStatus.FA);
                }
            }
        }
        return result;

    }
}