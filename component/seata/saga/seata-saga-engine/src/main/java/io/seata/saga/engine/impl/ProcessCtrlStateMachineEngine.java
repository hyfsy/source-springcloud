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
package io.seata.saga.engine.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.util.CollectionUtils;
import io.seata.saga.engine.AsyncCallback;
import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.StateMachineEngine;
import io.seata.saga.engine.exception.EngineExecutionException;
import io.seata.saga.engine.exception.ForwardInvalidException;
import io.seata.saga.engine.pcext.StateInstruction;
import io.seata.saga.engine.pcext.utils.EngineUtils;
import io.seata.saga.engine.pcext.utils.LoopTaskUtils;
import io.seata.saga.engine.pcext.utils.ParameterUtils;
import io.seata.saga.engine.utils.ProcessContextBuilder;
import io.seata.saga.proctrl.ProcessContext;
import io.seata.saga.proctrl.ProcessType;
import io.seata.saga.statelang.domain.DomainConstants;
import io.seata.saga.statelang.domain.ExecutionStatus;
import io.seata.saga.statelang.domain.State;
import io.seata.saga.statelang.domain.StateInstance;
import io.seata.saga.statelang.domain.StateMachine;
import io.seata.saga.statelang.domain.StateMachineInstance;
import io.seata.saga.statelang.domain.TaskState.Loop;
import io.seata.saga.statelang.domain.impl.AbstractTaskState;
import io.seata.saga.statelang.domain.impl.CompensationTriggerStateImpl;
import io.seata.saga.statelang.domain.impl.LoopStartStateImpl;
import io.seata.saga.statelang.domain.impl.ServiceTaskStateImpl;
import io.seata.saga.statelang.domain.impl.StateMachineInstanceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * ProcessCtrl-based state machine engine
 *
 * @author lorne.cl
 */
public class ProcessCtrlStateMachineEngine implements StateMachineEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCtrlStateMachineEngine.class);

    private StateMachineConfig stateMachineConfig;

    private static void nullSafeCopy(Map<String, Object> srcMap, Map<String, Object> destMap) {
        srcMap.forEach((key, value) -> {
            if (value != null) {
                destMap.put(key, value);
            }
        });
    }

    @Override
    public StateMachineInstance start(String stateMachineName, String tenantId, Map<String, Object> startParams)
        throws EngineExecutionException {

        return startInternal(stateMachineName, tenantId, null, startParams, false, null);
    }

    @Override
    public StateMachineInstance startAsync(String stateMachineName, String tenantId, Map<String, Object> startParams,
                                           AsyncCallback callback) throws EngineExecutionException {

        return startInternal(stateMachineName, tenantId, null, startParams, true, callback);
    }

    @Override
    public StateMachineInstance startWithBusinessKey(String stateMachineName, String tenantId, String businessKey,
                                                     Map<String, Object> startParams) throws EngineExecutionException {

        return startInternal(stateMachineName, tenantId, businessKey, startParams, false, null);
    }

    @Override
    public StateMachineInstance startWithBusinessKeyAsync(String stateMachineName, String tenantId, String businessKey,
                                                          Map<String, Object> startParams, AsyncCallback callback)
        throws EngineExecutionException {

        return startInternal(stateMachineName, tenantId, businessKey, startParams, true, callback);
    }

    private StateMachineInstance startInternal(String stateMachineName, String tenantId, String businessKey,
                                               Map<String, Object> startParams, boolean async, AsyncCallback callback)
        throws EngineExecutionException {

        if (async && !stateMachineConfig.isEnableAsync()) {
            throw new EngineExecutionException(
                "Asynchronous start is disabled. please set StateMachineConfig.enableAsync=true first.",
                FrameworkErrorCode.AsynchronousStartDisabled);
        }

        if (StringUtils.isEmpty(tenantId)) {
            tenantId = stateMachineConfig.getDefaultTenantId();
        }

        // 通过 stateMachineName 和 *.json 创建一个状态机
        StateMachineInstance instance = createMachineInstance(stateMachineName, tenantId, businessKey, startParams);

        // 初始化处理上下文，包含所有的东西
        ProcessContextBuilder contextBuilder = ProcessContextBuilder.create().withProcessType(ProcessType.STATE_LANG)
            .withOperationName(DomainConstants.OPERATION_NAME_START).withAsyncCallback(callback).withInstruction(
                new StateInstruction(stateMachineName, tenantId)).withStateMachineInstance(instance)
            .withStateMachineConfig(getStateMachineConfig()).withStateMachineEngine(this);

        Map<String, Object> contextVariables;
        if (startParams != null) {
            contextVariables = new ConcurrentHashMap<>(startParams.size());
            nullSafeCopy(startParams, contextVariables);
        } else {
            contextVariables = new ConcurrentHashMap<>();
        }
        instance.setContext(contextVariables);

        contextBuilder.withStateMachineContextVariables(contextVariables);

        contextBuilder.withIsAsyncExecution(async);

        ProcessContext processContext = contextBuilder.build();

        // 持久化状态机，标记状态机启动
        // 状态机启动意味着全局事务开启，向TC报告
        // 状态启动意味着分支事务要开始处理了
        if (instance.getStateMachine().isPersist() && stateMachineConfig.getStateLogStore() != null) {
            stateMachineConfig.getStateLogStore().recordStateMachineStarted(instance, processContext);
        }
        if (StringUtils.isEmpty(instance.getId())) {
            instance.setId(
                stateMachineConfig.getSeqGenerator().generate(DomainConstants.SEQ_ENTITY_STATE_MACHINE_INST));
        }

        // 添加loop状态的临时状态
        StateInstruction stateInstruction = processContext.getInstruction(StateInstruction.class);
        Loop loop = LoopTaskUtils.getLoopConfig(processContext, stateInstruction.getState(processContext));
        if (null != loop) {
            stateInstruction.setTemporaryState(new LoopStartStateImpl());
        }

        // 扔线程池里跑，没有栈的概念
        if (async) {
            stateMachineConfig.getAsyncProcessCtrlEventPublisher().publish(processContext);
        }
        // 本地直接跑
        else {
            stateMachineConfig.getProcessCtrlEventPublisher().publish(processContext);
        }

        return instance;
    }

    private StateMachineInstance createMachineInstance(String stateMachineName, String tenantId, String businessKey,
                                                       Map<String, Object> startParams) {
        // *.json解析的状态机内存实例
        StateMachine stateMachine = stateMachineConfig.getStateMachineRepository().getStateMachine(stateMachineName,
            tenantId);
        if (stateMachine == null) {
            throw new EngineExecutionException("StateMachine[" + stateMachineName + "] is not exists",
                FrameworkErrorCode.ObjectNotExists);
        }

        StateMachineInstanceImpl inst = new StateMachineInstanceImpl();
        inst.setStateMachine(stateMachine);
        inst.setMachineId(stateMachine.getId());
        inst.setTenantId(tenantId);
        inst.setBusinessKey(businessKey);

        inst.setStartParams(startParams);
        if (startParams != null) {
            if (StringUtils.hasText(businessKey)) {
                startParams.put(DomainConstants.VAR_NAME_BUSINESSKEY, businessKey);
            }

            // SubStateMachine才有
            String parentId = (String)startParams.get(DomainConstants.VAR_NAME_PARENT_ID);
            if (StringUtils.hasText(parentId)) {
                inst.setParentId(parentId);
                startParams.remove(DomainConstants.VAR_NAME_PARENT_ID);
            }
        }

        inst.setStatus(ExecutionStatus.RU);

        inst.setRunning(true);

        inst.setGmtStarted(new Date());
        inst.setGmtUpdated(inst.getGmtStarted());

        return inst;
    }

    @Override
    public StateMachineInstance forward(String stateMachineInstId, Map<String, Object> replaceParams)
        throws EngineExecutionException {
        return forwardInternal(stateMachineInstId, replaceParams, false, false, null);
    }

    @Override
    public StateMachineInstance forwardAsync(String stateMachineInstId, Map<String, Object> replaceParams,
                                             AsyncCallback callback) throws EngineExecutionException {
        return forwardInternal(stateMachineInstId, replaceParams, false, true, callback);
    }

    protected StateMachineInstance forwardInternal(String stateMachineInstId, Map<String, Object> replaceParams,
                                                   boolean skip, boolean async, AsyncCallback callback)
        throws EngineExecutionException {

        // 获取状态机数据库实体，缓存没有就查库
        StateMachineInstance stateMachineInstance = reloadStateMachineInstance(stateMachineInstId);

        if (stateMachineInstance == null) {
            throw new ForwardInvalidException("StateMachineInstance is not exits",
                FrameworkErrorCode.StateMachineInstanceNotExists);
        }
        // 已经成功了，直接返回
        if (ExecutionStatus.SU.equals(stateMachineInstance.getStatus())
            && stateMachineInstance.getCompensationStatus() == null) {
            return stateMachineInstance;
        }

        // 检查当前的状态机状态，有问题就抛异常
        ExecutionStatus[] acceptStatus = new ExecutionStatus[] {ExecutionStatus.FA, ExecutionStatus.UN, ExecutionStatus.RU};
        checkStatus(stateMachineInstance, acceptStatus, null, stateMachineInstance.getStatus(), null, "forward");

        // 向前的状态机没有状态实体，数据就存在问题
        List<StateInstance> actList = stateMachineInstance.getStateList();
        if (CollectionUtils.isEmpty(actList)) {
            throw new ForwardInvalidException("StateMachineInstance[id:" + stateMachineInstId
                + "] has no stateInstance, pls start a new StateMachine execution instead",
                FrameworkErrorCode.OperationDenied);
        }

        // 最后的一个处理状态实例
        StateInstance lastForwardState = findOutLastForwardStateInstance(actList);

        if (lastForwardState == null) {
            throw new ForwardInvalidException(
                "StateMachineInstance[id:" + stateMachineInstId + "] Cannot find last forward execution stateInstance",
                FrameworkErrorCode.OperationDenied);
        }

        // 构建处理上下文
        ProcessContextBuilder contextBuilder = ProcessContextBuilder.create().withProcessType(ProcessType.STATE_LANG)
            .withOperationName(DomainConstants.OPERATION_NAME_FORWARD).withAsyncCallback(callback)
            .withStateMachineInstance(stateMachineInstance).withStateInstance(lastForwardState).withStateMachineConfig(
                getStateMachineConfig()).withStateMachineEngine(this);

        contextBuilder.withIsAsyncExecution(async);

        ProcessContext context = contextBuilder.build();

        // 获取上下文参数
        Map<String, Object> contextVariables = getStateMachineContextVariables(stateMachineInstance);

        if (replaceParams != null) {
            contextVariables.putAll(replaceParams);
        }
        putBusinesskeyToContextariables(stateMachineInstance, contextVariables);

        ConcurrentHashMap<String, Object> concurrentContextVariables = new ConcurrentHashMap<>(contextVariables.size());
        nullSafeCopy(contextVariables, concurrentContextVariables);

        context.setVariable(DomainConstants.VAR_NAME_STATEMACHINE_CONTEXT, concurrentContextVariables);
        stateMachineInstance.setContext(concurrentContextVariables);

        // 获取最后一个状态，这个状态是配置文件解析出来的，不是数据库的
        String originStateName = EngineUtils.getOriginStateName(lastForwardState);
        State lastState = stateMachineInstance.getStateMachine().getState(originStateName);

        // 当前状态是成功，尝试从循环配置里获取最终需要forward的状态
        Loop loop = LoopTaskUtils.getLoopConfig(context, lastState);
        if (null != loop && ExecutionStatus.SU.equals(lastForwardState.getStatus())) {
            lastForwardState = LoopTaskUtils.findOutLastNeedForwardStateInstance(context);
        }

        // 放入重试的状态id
        context.setVariable(lastForwardState.getName() + DomainConstants.VAR_NAME_RETRIED_STATE_INST_ID,
            lastForwardState.getId());
        // 放入前进的子状态机id
        if (DomainConstants.STATE_TYPE_SUB_STATE_MACHINE.equals(lastForwardState.getType()) && !ExecutionStatus.SU
            .equals(lastForwardState.getCompensationStatus())) {
            // 指定子状态机执行时用forward方法执行，非start方法执行
            context.setVariable(DomainConstants.VAR_NAME_IS_FOR_SUB_STATMACHINE_FORWARD, true);
        }

        // 普通的状态，先指定忽略，防止该状态被重新启动
        if (!ExecutionStatus.SU.equals(lastForwardState.getStatus())) {
            lastForwardState.setIgnoreStatus(true);
        }

        // 创建状态机流转的开始指令
        try {
            StateInstruction inst = new StateInstruction();
            inst.setTenantId(stateMachineInstance.getTenantId());
            inst.setStateMachineName(stateMachineInstance.getStateMachine().getName());
            // 当前状态成功了，寻找next的状态
            if (skip || ExecutionStatus.SU.equals(lastForwardState.getStatus())) {

                String next = null;
                State state = stateMachineInstance.getStateMachine().getState(EngineUtils.getOriginStateName(lastForwardState));
                if (state != null && state instanceof AbstractTaskState) {
                    next = ((AbstractTaskState)state).getNext();
                }
                if (StringUtils.isEmpty(next)) {
                    LOGGER.warn(
                        "Last Forward execution StateInstance was succeed, and it has not Next State , skip forward "
                            + "operation");
                    return stateMachineInstance;
                }
                inst.setStateName(next);
            }
            else {

                // 当前状态已经在运行了，抛个异常
                if (ExecutionStatus.RU.equals(lastForwardState.getStatus())
                        && !EngineUtils.isTimeout(lastForwardState.getGmtStarted(), stateMachineConfig.getServiceInvokeTimeout())) {
                    throw new EngineExecutionException(
                            "State [" + lastForwardState.getName() + "] is running, operation[forward] denied", FrameworkErrorCode.OperationDenied);
                }

                // 设置指令运行的状态名称
                inst.setStateName(EngineUtils.getOriginStateName(lastForwardState));
            }
            context.setInstruction(inst);

            // 启动状态机
            stateMachineInstance.setStatus(ExecutionStatus.RU);
            stateMachineInstance.setRunning(true);

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Operation [forward] started  stateMachineInstance[id:" + stateMachineInstance.getId() + "]");
            }

            // 记录重新开始，更新下 is_running 和 gmt_updated
            if (stateMachineInstance.getStateMachine().isPersist()) {
                stateMachineConfig.getStateLogStore().recordStateMachineRestarted(stateMachineInstance, context);
            }

            loop = LoopTaskUtils.getLoopConfig(context, inst.getState(context));
            if (null != loop) {
                inst.setTemporaryState(new LoopStartStateImpl());
            }

            if (async) {
                stateMachineConfig.getAsyncProcessCtrlEventPublisher().publish(context);
            } else {
                stateMachineConfig.getProcessCtrlEventPublisher().publish(context);
            }
        } catch (EngineExecutionException e) {
            LOGGER.error("Operation [forward] failed", e);
            throw e;
        }
        return stateMachineInstance;
    }

    private Map<String, Object> getStateMachineContextVariables(StateMachineInstance stateMachineInstance) {
        // 有返回参数的话，直接返回，这个参数是某个状态返回的
        Map<String, Object> contextVariables = stateMachineInstance.getEndParams();
        if (CollectionUtils.isEmpty(contextVariables)) {
            contextVariables = replayContextVariables(stateMachineInstance);
        }
        return contextVariables;
    }

    protected Map<String, Object> replayContextVariables(StateMachineInstance stateMachineInstance) {
        Map<String, Object> contextVariables = new HashMap<>();

        // 上下文还原两种参数

        // startParams
        if (stateMachineInstance.getStartParams() == null) {
            contextVariables.putAll(stateMachineInstance.getStartParams());
        }

        List<StateInstance> stateInstanceList = stateMachineInstance.getStateList();
        if (CollectionUtils.isEmpty(stateInstanceList)) {
            return contextVariables;
        }

        // outputParams
        for (StateInstance stateInstance : stateInstanceList) {
            Object serviceOutputParams = stateInstance.getOutputParams();
            if (serviceOutputParams != null) {
                // ServiceTask 状态才会有返回值
                ServiceTaskStateImpl state = (ServiceTaskStateImpl)stateMachineInstance.getStateMachine().getState(
                        EngineUtils.getOriginStateName(stateInstance));
                if (state == null) {
                    throw new EngineExecutionException(
                            "Cannot find State by state name [" + stateInstance.getName() + "], may be this is a bug",
                            FrameworkErrorCode.ObjectNotExists);
                }

                if (CollectionUtils.isNotEmpty(state.getOutput())) {
                    try {
                        // outputParams可以是一个对象或可解析为json的对象
                        Map<String, Object> outputVariablesToContext = ParameterUtils
                                .createOutputParams(stateMachineConfig.getExpressionFactoryManager(), state,
                                        serviceOutputParams);
                        if (CollectionUtils.isNotEmpty(outputVariablesToContext)) {
                            contextVariables.putAll(outputVariablesToContext);
                        }

                        if (StringUtils.hasLength(stateInstance.getBusinessKey())) {
                            contextVariables.put(
                                    state.getName() + DomainConstants.VAR_NAME_BUSINESSKEY,
                                    stateInstance.getBusinessKey());
                        }
                    } catch (Exception e) {
                        throw new EngineExecutionException(e, "Context variables replay faied",
                                FrameworkErrorCode.ContextVariableReplayFailed);
                    }
                }
            }
        }
        return contextVariables;
    }

    /**
     * Find the last instance of the forward execution state
     *
     * @param stateInstanceList
     * @return
     */
    public StateInstance findOutLastForwardStateInstance(List<StateInstance> stateInstanceList) {
        StateInstance lastForwardStateInstance = null;
        // 从后向前
        for (int i = stateInstanceList.size() - 1; i >= 0; i--) {
            StateInstance stateInstance = stateInstanceList.get(i);
            // 不是为了补偿生成的状态
            if (!stateInstance.isForCompensation()) {

                // 补偿成功的忽略
                if (ExecutionStatus.SU.equals(stateInstance.getCompensationStatus())) {
                    continue;
                }

                // 子状态机的情况
                if (DomainConstants.STATE_TYPE_SUB_STATE_MACHINE.equals(stateInstance.getType())) {

                    StateInstance finalState = stateInstance;

                    // 获取最初出错的状态
                    while (StringUtils.hasText(finalState.getStateIdRetriedFor())) {
                        finalState = stateMachineConfig.getStateLogStore().getStateInstance(
                            finalState.getStateIdRetriedFor(), finalState.getMachineInstanceId());
                    }

                    List<StateMachineInstance> subInst = stateMachineConfig.getStateLogStore()
                        .queryStateMachineInstanceByParentId(EngineUtils.generateParentId(finalState));
                    if (CollectionUtils.isNotEmpty(subInst)) {
                        if (ExecutionStatus.SU.equals(subInst.get(0).getCompensationStatus())) {
                            continue;
                        }

                        // 尝试补偿过了，不能继续forward
                        if (ExecutionStatus.UN.equals(subInst.get(0).getCompensationStatus())) {
                            throw new ForwardInvalidException(
                                "Last forward execution state instance is SubStateMachine and compensation status is "
                                    + "[UN], Operation[forward] denied, stateInstanceId:"
                                    + stateInstance.getId(), FrameworkErrorCode.OperationDenied);
                        }

                    }
                }
                // 尝试补偿过了，不能继续forward
                else if (ExecutionStatus.UN.equals(stateInstance.getCompensationStatus())) {

                    throw new ForwardInvalidException(
                        "Last forward execution state instance compensation status is [UN], Operation[forward] "
                            + "denied, stateInstanceId:"
                            + stateInstance.getId(), FrameworkErrorCode.OperationDenied);
                }

                lastForwardStateInstance = stateInstance;
                break;
            }
        }
        return lastForwardStateInstance;
    }

    @Override
    public StateMachineInstance compensate(String stateMachineInstId, Map<String, Object> replaceParams)
        throws EngineExecutionException {
        return compensateInternal(stateMachineInstId, replaceParams, false, null);
    }

    @Override
    public StateMachineInstance compensateAsync(String stateMachineInstId, Map<String, Object> replaceParams,
                                                AsyncCallback callback) throws EngineExecutionException {
        return compensateInternal(stateMachineInstId, replaceParams, true, callback);
    }

    public StateMachineInstance compensateInternal(String stateMachineInstId, Map<String, Object> replaceParams,
                                                   boolean async, AsyncCallback callback)
        throws EngineExecutionException {

        // 加载出之前执行的状态机
        StateMachineInstance stateMachineInstance = reloadStateMachineInstance(stateMachineInstId);

        if (stateMachineInstance == null) {
            throw new EngineExecutionException("StateMachineInstance is not exits",
                FrameworkErrorCode.StateMachineInstanceNotExists);
        }

        // 补偿成功过了，直接返回
        if (ExecutionStatus.SU.equals(stateMachineInstance.getCompensationStatus())) {
            return stateMachineInstance;
        }

        if (stateMachineInstance.getCompensationStatus() != null) {
            ExecutionStatus[] denyStatus = new ExecutionStatus[] {ExecutionStatus.SU};
            checkStatus(stateMachineInstance, null, denyStatus, null, stateMachineInstance.getCompensationStatus(),
                "compensate");
        }

        // 替换参数
        if (replaceParams != null) {
            stateMachineInstance.getEndParams().putAll(replaceParams);
        }

        ProcessContextBuilder contextBuilder = ProcessContextBuilder.create().withProcessType(ProcessType.STATE_LANG)
            .withOperationName(DomainConstants.OPERATION_NAME_COMPENSATE).withAsyncCallback(callback)
            .withStateMachineInstance(stateMachineInstance).withStateMachineConfig(getStateMachineConfig())
            .withStateMachineEngine(this);

        contextBuilder.withIsAsyncExecution(async);

        ProcessContext context = contextBuilder.build();

        Map<String, Object> contextVariables = getStateMachineContextVariables(stateMachineInstance);

        if (replaceParams != null) {
            contextVariables.putAll(replaceParams);
        }
        putBusinesskeyToContextariables(stateMachineInstance, contextVariables);

        ConcurrentHashMap<String, Object> concurrentContextVariables = new ConcurrentHashMap<>(contextVariables.size());
        nullSafeCopy(contextVariables, concurrentContextVariables);

        context.setVariable(DomainConstants.VAR_NAME_STATEMACHINE_CONTEXT, concurrentContextVariables);
        stateMachineInstance.setContext(concurrentContextVariables);

        // 启动补偿状态来进入到补偿流程
        CompensationTriggerStateImpl tempCompensationTriggerState = new CompensationTriggerStateImpl();
        tempCompensationTriggerState.setStateMachine(stateMachineInstance.getStateMachine());

        stateMachineInstance.setRunning(true);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Operation [compensate] start.  stateMachineInstance[id:" + stateMachineInstance.getId() + "]");
        }

        if (stateMachineInstance.getStateMachine().isPersist()) {
            stateMachineConfig.getStateLogStore().recordStateMachineRestarted(stateMachineInstance, context);
        }
        try {
            // 开始状态为补偿触发状态，直接进入到补偿流程
            StateInstruction inst = new StateInstruction();
            inst.setTenantId(stateMachineInstance.getTenantId());
            inst.setStateMachineName(stateMachineInstance.getStateMachine().getName());
            inst.setTemporaryState(tempCompensationTriggerState);

            context.setInstruction(inst);

            if (async) {
                stateMachineConfig.getAsyncProcessCtrlEventPublisher().publish(context);
            } else {
                stateMachineConfig.getProcessCtrlEventPublisher().publish(context);
            }

        } catch (EngineExecutionException e) {
            LOGGER.error("Operation [compensate] failed", e);
            throw e;
        }

        return stateMachineInstance;
    }

    @Override
    public StateMachineInstance skipAndForward(String stateMachineInstId, Map<String, Object> replaceParams) throws EngineExecutionException {
        return forwardInternal(stateMachineInstId, replaceParams, false, true, null);
    }

    @Override
    public StateMachineInstance skipAndForwardAsync(String stateMachineInstId, AsyncCallback callback)
        throws EngineExecutionException {
        return forwardInternal(stateMachineInstId, null, false, true, callback);
    }

    /**
     * override state machine instance
     *
     * @param instId
     * @return
     */
    @Override
    public StateMachineInstance reloadStateMachineInstance(String instId) {

        // 查询出状态机数据库实体
        StateMachineInstance inst = stateMachineConfig.getStateLogStore().getStateMachineInstance(instId);
        if (inst != null) {
            // 配置文件解析出的状态机实体，引擎执行时一般不改变该实体对象
            StateMachine stateMachine = inst.getStateMachine();
            if (stateMachine == null) {
                stateMachine = stateMachineConfig.getStateMachineRepository().getStateMachineById(inst.getMachineId());
                inst.setStateMachine(stateMachine);
            }
            if (stateMachine == null) {
                throw new EngineExecutionException("StateMachine[id:" + inst.getMachineId() + "] not exist.",
                    FrameworkErrorCode.ObjectNotExists);
            }

            // 状态的数据库实体
            List<StateInstance> stateList = inst.getStateList();
            if (CollectionUtils.isEmpty(stateList)) {
                stateList = stateMachineConfig.getStateLogStore().queryStateInstanceListByMachineInstanceId(instId);
                if (CollectionUtils.isNotEmpty(stateList)) {
                    for (StateInstance tmpStateInstance : stateList) {
                        inst.putStateInstance(tmpStateInstance.getId(), tmpStateInstance);
                    }
                }
            }

            if (CollectionUtils.isEmpty(inst.getEndParams())) {
                inst.setEndParams(replayContextVariables(inst));
            }
        }
        return inst;
    }

    /**
     * Check if the status is legal
     *
     * @param stateMachineInstance
     * @param acceptStatus
     * @param denyStatus
     * @param status
     * @param compenStatus
     * @param operation
     * @return
     */
    protected boolean checkStatus(StateMachineInstance stateMachineInstance, ExecutionStatus[] acceptStatus,
                                  ExecutionStatus[] denyStatus, ExecutionStatus status, ExecutionStatus compenStatus,
                                  String operation) {

        // 检查状态表示为要重新开始继续运行状态机

        // 状态和补偿状态不能同时存在
        if (status != null && compenStatus != null) {
            throw new EngineExecutionException("status and compensationStatus are not supported at the same time",
                FrameworkErrorCode.InvalidParameter);
        }
        // 状态和补偿状态不能都不存在
        if (status == null && compenStatus == null) {
            throw new EngineExecutionException("status and compensationStatus must input at least one",
                FrameworkErrorCode.InvalidParameter);
        }
        // 补偿状态不能为success
        if (ExecutionStatus.SU.equals(compenStatus)) {
            String message = buildExceptionMessage(stateMachineInstance, null, null, null, ExecutionStatus.SU,
                operation);
            throw new EngineExecutionException(message, FrameworkErrorCode.OperationDenied);
        }

        // 检查状态的时候，状态机还在运行，很明显有问题
        if (stateMachineInstance.isRunning() && !EngineUtils.isTimeout(stateMachineInstance.getGmtUpdated(), stateMachineConfig.getTransOperationTimeout())) {
            throw new EngineExecutionException(
                "StateMachineInstance [id:" + stateMachineInstance.getId() + "] is running, operation[" + operation
                    + "] denied", FrameworkErrorCode.OperationDenied);
        }

        // 拒绝和接收的状态不能都没有
        if ((denyStatus == null || denyStatus.length == 0) && (acceptStatus == null || acceptStatus.length == 0)) {
            throw new EngineExecutionException("StateMachineInstance[id:" + stateMachineInstance.getId()
                + "], acceptable status and deny status must input at least one", FrameworkErrorCode.InvalidParameter);
        }

        // 检查的状态
        ExecutionStatus currentStatus = (status != null) ? status : compenStatus;

        // 拒绝优先
        // 校验是否拒绝该状态
        if (!(denyStatus == null || denyStatus.length == 0)) {
            for (ExecutionStatus tempDenyStatus : denyStatus) {
                if (tempDenyStatus.compareTo(currentStatus) == 0) {
                    String message = buildExceptionMessage(stateMachineInstance, acceptStatus, denyStatus, status,
                        compenStatus, operation);
                    throw new EngineExecutionException(message, FrameworkErrorCode.OperationDenied);
                }
            }
        }

        // 校验是否支持该状态
        if (acceptStatus == null || acceptStatus.length == 0) {
            return true;
        } else {
            for (ExecutionStatus tempStatus : acceptStatus) {
                if (tempStatus.compareTo(currentStatus) == 0) {
                    return true;
                }
            }
        }

        // 默认抛异常
        String message = buildExceptionMessage(stateMachineInstance, acceptStatus, denyStatus, status, compenStatus,
            operation);
        throw new EngineExecutionException(message, FrameworkErrorCode.OperationDenied);
    }

    private String buildExceptionMessage(StateMachineInstance stateMachineInstance, ExecutionStatus[] acceptStatus,
                                         ExecutionStatus[] denyStatus, ExecutionStatus status,
                                         ExecutionStatus compenStatus, String operation) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("StateMachineInstance[id:").append(stateMachineInstance.getId()).append("]");
        if (acceptStatus != null) {
            stringBuilder.append(",acceptable status :");
            for (ExecutionStatus tempStatus : acceptStatus) {
                stringBuilder.append(tempStatus.toString());
                stringBuilder.append(" ");
            }
        }
        if (denyStatus != null) {
            stringBuilder.append(",deny status:");
            for (ExecutionStatus tempStatus : denyStatus) {
                stringBuilder.append(tempStatus.toString());
                stringBuilder.append(" ");
            }
        }
        if (status != null) {
            stringBuilder.append(",current status:");
            stringBuilder.append(status.toString());
        }
        if (compenStatus != null) {
            stringBuilder.append(",current compensation status:");
            stringBuilder.append(compenStatus.toString());
        }
        stringBuilder.append(",so operation [").append(operation).append("] denied");
        return stringBuilder.toString();
    }

    private void putBusinesskeyToContextariables(StateMachineInstance stateMachineInstance,
                                                 Map<String, Object> contextVariables) {
        if (StringUtils.hasText(stateMachineInstance.getBusinessKey()) && !contextVariables.containsKey(
            DomainConstants.VAR_NAME_BUSINESSKEY)) {
            contextVariables.put(DomainConstants.VAR_NAME_BUSINESSKEY, stateMachineInstance.getBusinessKey());
        }
    }

    @Override
    public StateMachineConfig getStateMachineConfig() {
        return stateMachineConfig;
    }

    public void setStateMachineConfig(StateMachineConfig stateMachineConfig) {
        this.stateMachineConfig = stateMachineConfig;
    }
}