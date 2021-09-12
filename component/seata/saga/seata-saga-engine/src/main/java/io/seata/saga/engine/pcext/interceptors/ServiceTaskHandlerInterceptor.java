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
package io.seata.saga.engine.pcext.interceptors;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.loader.LoadLevel;
import io.seata.common.util.CollectionUtils;
import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.evaluation.Evaluator;
import io.seata.saga.engine.evaluation.EvaluatorFactory;
import io.seata.saga.engine.evaluation.EvaluatorFactoryManager;
import io.seata.saga.engine.evaluation.expression.ExpressionEvaluator;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.pcext.InterceptableStateHandler;
import io.seata.saga.engine.pcext.StateHandlerInterceptor;
import io.seata.saga.engine.pcext.StateInstruction;
import io.seata.saga.engine.pcext.handlers.ServiceTaskStateHandler;
import io.seata.saga.engine.pcext.handlers.SubStateMachineHandler;
import io.seata.saga.engine.pcext.utils.CompensationHolder;
import io.seata.saga.engine.pcext.utils.EngineUtils;
import io.seata.saga.engine.pcext.utils.LoopTaskUtils;
import io.seata.saga.engine.pcext.utils.ParameterUtils;
import io.seata.saga.engine.utils.ExceptionUtils;
import io.seata.saga.proctrl.HierarchicalProcessContext;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.StateInstance;
import io.seata.saga.statelang.domain.StateMachineInstance;
import io.seata.saga.statelang.domain.impl.ServiceTaskStateImpl;
import io.seata.saga.statelang.domain.impl.StateInstanceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * StateInterceptor for ServiceTask, SubStateMachine, CompensateState
 *
 * @author lorne.cl
 */
@LoadLevel(name = "ServiceTask", order = 100)
public class ServiceTaskHandlerInterceptor implements StateHandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTaskHandlerInterceptor.class);

    @Override
    public boolean match(Class<? extends InterceptableStateHandler> clazz) {
        return clazz != null &&
                (ServiceTaskStateHandler.class.isAssignableFrom(clazz)
                || SubStateMachineHandler.class.isAssignableFrom(clazz));
    }

    @Override
    public void preProcess(ProcessContext context) throws EngineExecutionException {

        // 当前状态指令
        StateInstruction instruction = context.getInstruction(StateInstruction.class);

        // 引擎使用的状态机实体
        StateMachineInstance stateMachineInstance = (StateMachineInstance)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_INST);
        // 状态机配置
        StateMachineConfig stateMachineConfig = (StateMachineConfig)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_CONFIG);

        // 超时处理，直接结束流程
        if (EngineUtils.isTimeout(stateMachineInstance.getGmtUpdated(), stateMachineConfig.getTransOperationTimeout())) {
            String message = "Saga Transaction [stateMachineInstanceId:" + stateMachineInstance.getId()
                    + "] has timed out, stop execution now.";

            LOGGER.error(message);

            // 创建异常
            EngineExecutionException exception = ExceptionUtils.createEngineExecutionException(null,
                    FrameworkErrorCode.StateMachineExecutionTimeout, message, stateMachineInstance, instruction.getStateName());

            // 持久化状态机信息
            EngineUtils.failStateMachine(context, exception);

            throw exception;
        }

        StateInstanceImpl stateInstance = new StateInstanceImpl();

        Map<String, Object> contextVariables = (Map<String, Object>)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_CONTEXT);
        // 获取当前指令对应的状态实体
        ServiceTaskStateImpl state = (ServiceTaskStateImpl)instruction.getState(context);

        // 解析状态机的入参值
        List<Object> serviceInputParams = null;
        if (contextVariables != null) {
            try {
                serviceInputParams = ParameterUtils.createInputParams(stateMachineConfig.getExpressionFactoryManager(), stateInstance,
                    state, contextVariables);
            } catch (Exception e) {

                // 解析表达式失败

                String message = "Task [" + state.getName()
                    + "] input parameters assign failed, please check 'Input' expression:" + e.getMessage();

                EngineExecutionException exception = ExceptionUtils.createEngineExecutionException(e,
                    FrameworkErrorCode.VariablesAssignError, message, stateMachineInstance, state.getName());

                EngineUtils.failStateMachine(context, exception);

                throw exception;
            }
        }

        ((HierarchicalProcessContext)context).setVariableLocally(DomainConstants.VAR_NAME_INPUT_PARAMS,
            serviceInputParams);

        stateInstance.setMachineInstanceId(stateMachineInstance.getId());
        stateInstance.setStateMachineInstance(stateMachineInstance);
        Object isForCompensation = state.isForCompensation(); // 被其他状态指定了补偿状态的状态默认为true，也可配置

        // loop状态 & 不是补偿状态
        if (context.hasVariable(DomainConstants.VAR_NAME_IS_LOOP_STATE) && !Boolean.TRUE.equals(isForCompensation)) {
            stateInstance.setName(LoopTaskUtils.generateLoopStateName(context, state.getName()));
            StateInstance lastRetriedStateInstance = LoopTaskUtils.findOutLastRetriedStateInstance(stateMachineInstance,
                stateInstance.getName());
            stateInstance.setStateIdRetriedFor(
                lastRetriedStateInstance == null ? null : lastRetriedStateInstance.getId());
        }
        // 正常执行流程
        else {
            stateInstance.setName(state.getName());
            stateInstance.setStateIdRetriedFor(
                (String)context.getVariable(state.getName() + DomainConstants.VAR_NAME_RETRIED_STATE_INST_ID));
        }
        stateInstance.setGmtStarted(new Date());
        stateInstance.setGmtUpdated(stateInstance.getGmtStarted());
        stateInstance.setStatus(ExecutionStatus.RU);

        // 没有使用过
        if (StringUtils.hasLength(stateInstance.getBusinessKey())) {

            ((Map<String, Object>)context.getVariable(DomainConstants.VAR_NAME_STATEMACHINE_CONTEXT)).put(
                state.getName() + DomainConstants.VAR_NAME_BUSINESSKEY, stateInstance.getBusinessKey());
        }

        stateInstance.setType(state.getType());

        stateInstance.setForUpdate(state.isForUpdate());
        stateInstance.setServiceName(state.getServiceName());
        stateInstance.setServiceMethod(state.getServiceMethod());
        stateInstance.setServiceType(state.getServiceType());

        // 补偿处理中，设置下关联关系
        if (isForCompensation != null && (Boolean)isForCompensation) {
            CompensationHolder compensationHolder = CompensationHolder.getCurrent(context, true);
            StateInstance stateToBeCompensated = compensationHolder.getStatesNeedCompensation().get(state.getName());
            if (stateToBeCompensated != null) {

                // 需要补偿的状态
                stateToBeCompensated.setCompensationState(stateInstance);
                // 当前正在补偿的状态
                stateInstance.setStateIdCompensatedFor(stateToBeCompensated.getId());
            } else {
                LOGGER.error("Compensation State[{}] has no state to compensate, maybe this is a bug.",
                    state.getName());
            }
            CompensationHolder.getCurrent(context, true).addForCompensationState(stateInstance.getName(),
                stateInstance);
        }

        // 正常的forward状态，全局提交流程，saga的提交的意义不太一样，代表了状态机重新流转，因为之前失败了
        if (DomainConstants.OPERATION_NAME_FORWARD.equals(context.getVariable(DomainConstants.VAR_NAME_OPERATION_NAME))
            && StringUtils.isEmpty(stateInstance.getStateIdRetriedFor()) && !state.isForCompensation()) {

            // 将生成这个重试状态的前一个失败状态设置为忽略，即不需要再继续执行了，因为已经生成了当前状态表示
            List<StateInstance> stateList = stateMachineInstance.getStateList();
            if (CollectionUtils.isNotEmpty(stateList)) {
                for (int i = stateList.size() - 1; i >= 0; i--) {
                    StateInstance executedState = stateList.get(i);

                    if (stateInstance.getName().equals(executedState.getName())) {
                        stateInstance.setStateIdRetriedFor(executedState.getId());
                        // find latest execute will ignore
                        executedState.setIgnoreStatus(true);
                        break;
                    }
                }
            }
        }

        stateInstance.setInputParams(serviceInputParams);

        // 状态机信息要持久化
        if (stateMachineInstance.getStateMachine().isPersist() && state.isPersist()
            && stateMachineConfig.getStateLogStore() != null) {

            try {
                stateMachineConfig.getStateLogStore().recordStateStarted(stateInstance, context);
            } catch (Exception e) {

                // 存储异常，直接结束

                String message = "Record state[" + state.getName() + "] started failed, stateMachineInstance[" + stateMachineInstance
                        .getId() + "], Reason: " + e.getMessage();

                EngineExecutionException exception = ExceptionUtils.createEngineExecutionException(e,
                        FrameworkErrorCode.ExceptionCaught, message, stateMachineInstance, state.getName());

                EngineUtils.failStateMachine(context, exception);

                throw exception;
            }
        }

        // 生成状态的id
        if (StringUtils.isEmpty(stateInstance.getId())) {
            stateInstance.setId(stateMachineConfig.getSeqGenerator().generate(DomainConstants.SEQ_ENTITY_STATE_INST));
        }
        // 最后真正的作用：状态实例放入状态机实例中
        stateMachineInstance.putStateInstance(stateInstance.getId(), stateInstance);
        ((HierarchicalProcessContext)context).setVariableLocally(DomainConstants.VAR_NAME_STATE_INST, stateInstance);
    }

    @Override
    public void postProcess(ProcessContext context, Exception exp) throws EngineExecutionException {

        StateInstruction instruction = context.getInstruction(StateInstruction.class);
        ServiceTaskStateImpl state = (ServiceTaskStateImpl)instruction.getState(context);

        StateMachineInstance stateMachineInstance = (StateMachineInstance)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_INST);
        StateInstance stateInstance = (StateInstance)context.getVariable(DomainConstants.VAR_NAME_STATE_INST);
        if (stateInstance == null || !stateMachineInstance.isRunning()) {
            LOGGER.warn("StateMachineInstance[id:" + stateMachineInstance.getId() + "] is end. stop running");
            return;
        }

        StateMachineConfig stateMachineConfig = (StateMachineConfig)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_CONFIG);

        if (exp == null) {
            exp = (Exception)context.getVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION);
        }
        stateInstance.setException(exp);

        // 计算异常对应的状态，支持SpEL
        decideExecutionStatus(context, stateInstance, state, exp);

        // 成功状态清除EX参数
        if (ExecutionStatus.SU.equals(stateInstance.getStatus()) && exp != null) {

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(
                    "Although an exception occurs, the execution status map to SU, and the exception is ignored when "
                        + "the execution status decision.");
            }
            context.removeVariable(DomainConstants.VAR_NAME_CURRENT_EXCEPTION);
        }

        // 合并startParams和返回值
        Map<String, Object> contextVariables = (Map<String, Object>)context.getVariable(
            DomainConstants.VAR_NAME_STATEMACHINE_CONTEXT);
        Object serviceOutputParams = context.getVariable(DomainConstants.VAR_NAME_OUTPUT_PARAMS);
        if (serviceOutputParams != null) {
            try {
                // 方法返回值也可以为SpEL表达式，会处理下
                Map<String, Object> outputVariablesToContext = ParameterUtils.createOutputParams(
                    stateMachineConfig.getExpressionFactoryManager(), state, serviceOutputParams);
                if (CollectionUtils.isNotEmpty(outputVariablesToContext)) {
                    contextVariables.putAll(outputVariablesToContext);
                }
            } catch (Exception e) {
                String message = "Task [" + state.getName()
                    + "] output parameters assign failed, please check 'Output' expression:" + e.getMessage();

                EngineExecutionException exception = ExceptionUtils.createEngineExecutionException(e,
                    FrameworkErrorCode.VariablesAssignError, message, stateMachineInstance, stateInstance);

                if (stateMachineInstance.getStateMachine().isPersist() && state.isPersist()
                    && stateMachineConfig.getStateLogStore() != null) {

                    stateMachineConfig.getStateLogStore().recordStateFinished(stateInstance, context);
                }

                EngineUtils.failStateMachine(context, exception);

                throw exception;
            }
        }

        // 不需要再使用了，移除
        context.removeVariable(DomainConstants.VAR_NAME_OUTPUT_PARAMS);
        context.removeVariable(DomainConstants.VAR_NAME_INPUT_PARAMS);

        stateInstance.setGmtEnd(new Date());

        // 持久化，分支事务结束
        if (stateMachineInstance.getStateMachine().isPersist() && state.isPersist()
            && stateMachineConfig.getStateLogStore() != null) {
            stateMachineConfig.getStateLogStore().recordStateFinished(stateInstance, context);
        }

        // 没有Catch处理过异常，这边额外处理下，直接结束当前状态机
        // Catch过的则不再抛异常了
        if (exp != null && context.getVariable(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH) != null
            && (Boolean)context.getVariable(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH)) {
            //If there is an exception and there is no catch, need to exit the state machine to execute.

            context.removeVariable(DomainConstants.VAR_NAME_IS_EXCEPTION_NOT_CATCH);
            EngineUtils.failStateMachine(context, exp);
        }

    }

    private void decideExecutionStatus(ProcessContext context, StateInstance stateInstance, ServiceTaskStateImpl state,
                                       Exception exp) {
        // 状态映射处理，配置的 Status
        Map<String, String> statusMatchList = state.getStatus();
        if (CollectionUtils.isNotEmpty(statusMatchList)) {
            if (state.isAsync()) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                        "Service[{}.{}] is execute asynchronously, null return value collected, so user defined "
                            + "Status Matching skipped. stateName: {}, branchId: {}", state.getServiceName(),
                        state.getServiceMethod(), state.getName(), stateInstance.getId());
                }
            } else {
                StateMachineConfig stateMachineConfig = (StateMachineConfig)context.getVariable(
                    DomainConstants.VAR_NAME_STATEMACHINE_CONFIG);

                // 初始化 Evaluator
                Map<Object, String> statusEvaluators = state.getStatusEvaluators();
                if (statusEvaluators == null) {
                    synchronized (state) {
                        statusEvaluators = state.getStatusEvaluators();
                        if (statusEvaluators == null) {
                            statusEvaluators = new LinkedHashMap<>(statusMatchList.size());
                            String expressionStr, statusVal;
                            Evaluator evaluator;
                            for (Map.Entry<String, String> entry : statusMatchList.entrySet()) {
                                expressionStr = entry.getKey();
                                statusVal = entry.getValue();
                                // 解析
                                evaluator = createEvaluator(stateMachineConfig.getEvaluatorFactoryManager(), expressionStr);
                                if (evaluator != null) {
                                    statusEvaluators.put(evaluator, statusVal);
                                }
                            }
                        }
                        state.setStatusEvaluators(statusEvaluators);
                    }
                }

                // 匹配，切换状态
                for (Object evaluatorObj : statusEvaluators.keySet()) {
                    Evaluator evaluator = (Evaluator)evaluatorObj;
                    String statusVal = statusEvaluators.get(evaluator);
                    if (evaluator.evaluate(context.getVariables())) {
                        stateInstance.setStatus(ExecutionStatus.valueOf(statusVal));
                        break;
                    }
                }

                // 流程结束，没有异常，但上面没有状态能匹配，有问题，需要直接抛异常了
                if (exp == null && (stateInstance.getStatus() == null || ExecutionStatus.RU.equals(
                    stateInstance.getStatus()))) {

                    if (state.isForUpdate()) {
                        stateInstance.setStatus(ExecutionStatus.UN);
                    } else {
                        stateInstance.setStatus(ExecutionStatus.FA);
                    }
                    stateInstance.setGmtEnd(new Date());

                    StateMachineInstance stateMachineInstance = stateInstance.getStateMachineInstance();

                    if (stateMachineInstance.getStateMachine().isPersist() && state.isPersist()
                        && stateMachineConfig.getStateLogStore() != null) {
                        stateMachineConfig.getStateLogStore().recordStateFinished(stateInstance, context);
                    }

                    EngineExecutionException exception = new EngineExecutionException("State [" + state.getName()
                        + "] execute finished, but cannot matching status, pls check its status manually",
                        FrameworkErrorCode.NoMatchedStatus);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("State[{}] execute finish with status[{}]", state.getName(),
                            stateInstance.getStatus());
                    }
                    EngineUtils.failStateMachine(context, exception);

                    throw exception;
                }
            }
        }

        // 正在运行的流程，上面没有匹配状态或匹配了没有成功的才会走这里
        if (stateInstance.getStatus() == null || ExecutionStatus.RU.equals(stateInstance.getStatus())) {

            // 没有异常，代表成功
            if (exp == null) {
                stateInstance.setStatus(ExecutionStatus.SU);
            }
            // 更新或补偿
            else {
                if (state.isForUpdate() || state.isForCompensation()) {

                    stateInstance.setStatus(ExecutionStatus.UN);
                    ExceptionUtils.NetExceptionType t = ExceptionUtils.getNetExceptionType(exp);
                    if (t != null) {
                        // 网络异常，直接失败
                        if (t.equals(ExceptionUtils.NetExceptionType.CONNECT_EXCEPTION)) {
                            stateInstance.setStatus(ExecutionStatus.FA);
                        } else if (t.equals(ExceptionUtils.NetExceptionType.READ_TIMEOUT_EXCEPTION)) {
                            stateInstance.setStatus(ExecutionStatus.UN);
                        }
                    } else {
                        stateInstance.setStatus(ExecutionStatus.UN);
                    }
                }
                // 失败
                else {
                    stateInstance.setStatus(ExecutionStatus.FA);
                }
            }
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("State[{}] finish with status[{}]", state.getName(), stateInstance.getStatus());
        }
    }

    private Evaluator createEvaluator(EvaluatorFactoryManager evaluatorFactoryManager, String expressionStr) {
        String expressionType = null;
        String expressionContent = null;
        Evaluator evaluator = null;
        if (StringUtils.hasLength(expressionStr)) {
            if (expressionStr.startsWith("$")) {
                int expTypeStart = expressionStr.indexOf("$");
                int expTypeEnd = expressionStr.indexOf("{", expTypeStart);

                if (expTypeStart >= 0 && expTypeEnd > expTypeStart) {
                    expressionType = expressionStr.substring(expTypeStart + 1, expTypeEnd);
                }

                int expEnd = expressionStr.lastIndexOf("}");
                if (expTypeEnd > 0 && expEnd > expTypeEnd) {
                    expressionContent = expressionStr.substring(expTypeEnd + 1, expEnd);
                }
            } else {
                expressionContent = expressionStr;
            }

            EvaluatorFactory evaluatorFactory = evaluatorFactoryManager.getEvaluatorFactory(expressionType);
            if (evaluatorFactory == null) {
                throw new IllegalArgumentException("Cannot get EvaluatorFactory by Type[" + expressionType + "]");
            }
            evaluator = evaluatorFactory.createEvaluator(expressionContent);
            if (evaluator instanceof ExpressionEvaluator) {
                ((ExpressionEvaluator)evaluator).setRootObjectName(DomainConstants.VAR_NAME_OUTPUT_PARAMS);
            }
        }
        return evaluator;
    }
}
