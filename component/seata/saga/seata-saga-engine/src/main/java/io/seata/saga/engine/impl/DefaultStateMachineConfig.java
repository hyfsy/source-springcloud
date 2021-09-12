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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.saga.engine.StateMachineConfig;
import io.seata.saga.engine.evaluation.EvaluatorFactoryManager;
import io.seata.saga.engine.evaluation.exception.ExceptionMatchEvaluatorFactory;
import io.seata.saga.engine.evaluation.expression.ExpressionEvaluatorFactory;
import io.seata.saga.engine.expression.ExpressionFactoryManager;
import io.seata.saga.engine.expression.seq.SequenceExpressionFactory;
import io.seata.saga.engine.expression.spel.SpringELExpressionFactory;
import io.seata.saga.engine.invoker.ServiceInvokerManager;
import io.seata.saga.engine.invoker.impl.SpringBeanServiceInvoker;
import io.seata.saga.engine.pcext.InterceptableStateHandler;
import io.seata.saga.engine.pcext.InterceptableStateRouter;
import io.seata.saga.engine.pcext.StateHandler;
import io.seata.saga.engine.pcext.StateHandlerInterceptor;
import io.seata.saga.engine.pcext.StateMachineProcessHandler;
import io.seata.saga.engine.pcext.StateMachineProcessRouter;
import io.seata.saga.engine.pcext.StateRouter;
import io.seata.saga.engine.pcext.StateRouterInterceptor;
import io.seata.saga.engine.repo.StateLogRepository;
import io.seata.saga.engine.repo.StateMachineRepository;
import io.seata.saga.engine.repo.impl.StateLogRepositoryImpl;
import io.seata.saga.engine.repo.impl.StateMachineRepositoryImpl;
import io.seata.saga.engine.sequence.SeqGenerator;
import io.seata.saga.engine.sequence.SpringJvmUUIDSeqGenerator;
import io.seata.saga.engine.store.StateLangStore;
import io.seata.saga.engine.store.StateLogStore;
import io.seata.saga.engine.strategy.StatusDecisionStrategy;
import io.seata.saga.engine.strategy.impl.DefaultStatusDecisionStrategy;
import io.seata.saga.proctrl.ProcessRouter;
import io.seata.saga.proctrl.ProcessType;
import io.seata.saga.proctrl.eventing.impl.AsyncEventBus;
import io.seata.saga.proctrl.eventing.impl.DirectEventBus;
import io.seata.saga.proctrl.eventing.impl.ProcessCtrlEventConsumer;
import io.seata.saga.proctrl.eventing.impl.ProcessCtrlEventPublisher;
import io.seata.saga.proctrl.handler.DefaultRouterHandler;
import io.seata.saga.proctrl.handler.ProcessHandler;
import io.seata.saga.proctrl.handler.RouterHandler;
import io.seata.saga.proctrl.impl.ProcessControllerImpl;
import io.seata.saga.proctrl.process.impl.CustomizeBusinessProcessor;
import io.seata.saga.statelang.domain.DomainConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;

import javax.script.ScriptEngineManager;

import static io.seata.common.DefaultValues.DEFAULT_CLIENT_SAGA_COMPENSATE_PERSIST_MODE_UPDATE;
import static io.seata.common.DefaultValues.DEFAULT_CLIENT_SAGA_RETRY_PERSIST_MODE_UPDATE;
import static io.seata.common.DefaultValues.DEFAULT_SAGA_JSON_PARSER;

/**
 * Default state machine configuration
 *
 * @author lorne.cl
 */
public class DefaultStateMachineConfig implements StateMachineConfig, ApplicationContextAware, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStateMachineConfig.class);

    private static final int DEFAULT_TRANS_OPER_TIMEOUT     = 60000 * 30;
    private static final int DEFAULT_SERVICE_INVOKE_TIMEOUT = 60000 * 5;

    // 分支事务超时
    private int transOperationTimeout = DEFAULT_TRANS_OPER_TIMEOUT;
    // ServiceTask状态的服务调用超时
    private int serviceInvokeTimeout  = DEFAULT_SERVICE_INVOKE_TIMEOUT;

    // 校验null的情况
    private StateLogRepository stateLogRepository;
    // CRUD 状态、状态机的数据，内部也会与TC交互
    private StateLogStore stateLogStore;
    // CRUD state-lang *.json处理过后的数据
    private StateLangStore stateLangStore;
    // EL表达式创建
    private ExpressionFactoryManager expressionFactoryManager;
    // EL表达式值计算
    private EvaluatorFactoryManager evaluatorFactoryManager;
    // CRUD状态机资源，state-lang.json
    private StateMachineRepository stateMachineRepository;
    // 决定状态机的最终状态
    private StatusDecisionStrategy statusDecisionStrategy;
    private SeqGenerator seqGenerator;

    // 状态事件发布器-同步
    private ProcessCtrlEventPublisher syncProcessCtrlEventPublisher;
    // 状态事件发布器-异步
    private ProcessCtrlEventPublisher asyncProcessCtrlEventPublisher;
    private ApplicationContext applicationContext;
    // 异步使用的线程池
    private ThreadPoolExecutor threadPoolExecutor;
    private boolean enableAsync;
    // ServiceTask调用服务对象的包装
    private ServiceInvokerManager serviceInvokerManager;

    // state-lang脚本资源，支持热刷新，需要自己编写代码
    private Resource[] resources = new Resource[0];
    private String charset = "UTF-8";
    private String defaultTenantId = "000001";
    // ScriptTask使用的JDK脚本引擎
    private ScriptEngineManager scriptEngineManager;
    // 对象序列化方式
    private String sagaJsonParser = DEFAULT_SAGA_JSON_PARSER;
    // 重试的情况下的持久化的日志是否更新，还是新增一条
    private boolean sagaRetryPersistModeUpdate = DEFAULT_CLIENT_SAGA_RETRY_PERSIST_MODE_UPDATE;
    // 补偿的情况下的持久化的日志是否更新，还是新增一条
    private boolean sagaCompensatePersistModeUpdate = DEFAULT_CLIENT_SAGA_COMPENSATE_PERSIST_MODE_UPDATE;

    protected void init() throws Exception {

        if (expressionFactoryManager == null) {
            expressionFactoryManager = new ExpressionFactoryManager();

            SpringELExpressionFactory springELExpressionFactory = new SpringELExpressionFactory();
            springELExpressionFactory.setApplicationContext(getApplicationContext());
            expressionFactoryManager.putExpressionFactory(ExpressionFactoryManager.DEFAULT_EXPRESSION_TYPE,
                springELExpressionFactory);

            SequenceExpressionFactory sequenceExpressionFactory = new SequenceExpressionFactory();
            sequenceExpressionFactory.setSeqGenerator(getSeqGenerator());
            expressionFactoryManager.putExpressionFactory(DomainConstants.EXPRESSION_TYPE_SEQUENCE,
                sequenceExpressionFactory);
        }

        if (evaluatorFactoryManager == null) {
            evaluatorFactoryManager = new EvaluatorFactoryManager();

            ExpressionEvaluatorFactory expressionEvaluatorFactory = new ExpressionEvaluatorFactory();
            expressionEvaluatorFactory.setExpressionFactory(
                expressionFactoryManager.getExpressionFactory(ExpressionFactoryManager.DEFAULT_EXPRESSION_TYPE));
            evaluatorFactoryManager.putEvaluatorFactory(EvaluatorFactoryManager.EVALUATOR_TYPE_DEFAULT,
                expressionEvaluatorFactory);

            evaluatorFactoryManager.putEvaluatorFactory(DomainConstants.EVALUATOR_TYPE_EXCEPTION,
                new ExceptionMatchEvaluatorFactory());
        }

        if (stateMachineRepository == null) {
            StateMachineRepositoryImpl stateMachineRepository = new StateMachineRepositoryImpl();
            stateMachineRepository.setCharset(charset);
            stateMachineRepository.setSeqGenerator(seqGenerator);
            stateMachineRepository.setStateLangStore(stateLangStore);
            stateMachineRepository.setDefaultTenantId(defaultTenantId);
            stateMachineRepository.setJsonParserName(sagaJsonParser);
            if (resources != null) {
                try {
                    stateMachineRepository.registryByResources(resources, defaultTenantId);
                } catch (IOException e) {
                    LOGGER.error("Load State Language Resources failed.", e);
                }
            }
            this.stateMachineRepository = stateMachineRepository;
        }

        if (stateLogRepository == null) {
            StateLogRepositoryImpl stateLogRepositoryImpl = new StateLogRepositoryImpl();
            stateLogRepositoryImpl.setStateLogStore(stateLogStore);
            this.stateLogRepository = stateLogRepositoryImpl;
        }

        if (statusDecisionStrategy == null) {
            statusDecisionStrategy = new DefaultStatusDecisionStrategy();
        }

        if (syncProcessCtrlEventPublisher == null) {
            ProcessCtrlEventPublisher syncEventPublisher = new ProcessCtrlEventPublisher();

            ProcessControllerImpl processorController = createProcessorController(syncEventPublisher);

            ProcessCtrlEventConsumer processCtrlEventConsumer = new ProcessCtrlEventConsumer();
            processCtrlEventConsumer.setProcessController(processorController);

            DirectEventBus directEventBus = new DirectEventBus();
            syncEventPublisher.setEventBus(directEventBus);

            directEventBus.registerEventConsumer(processCtrlEventConsumer);

            syncProcessCtrlEventPublisher = syncEventPublisher;
        }

        if (enableAsync && asyncProcessCtrlEventPublisher == null) {
            ProcessCtrlEventPublisher asyncEventPublisher = new ProcessCtrlEventPublisher();

            ProcessControllerImpl processorController = createProcessorController(asyncEventPublisher);

            ProcessCtrlEventConsumer processCtrlEventConsumer = new ProcessCtrlEventConsumer();
            processCtrlEventConsumer.setProcessController(processorController);

            AsyncEventBus asyncEventBus = new AsyncEventBus();
            asyncEventBus.setThreadPoolExecutor(getThreadPoolExecutor());
            asyncEventPublisher.setEventBus(asyncEventBus);

            asyncEventBus.registerEventConsumer(processCtrlEventConsumer);

            asyncProcessCtrlEventPublisher = asyncEventPublisher;
        }

        if (this.serviceInvokerManager == null) {
            this.serviceInvokerManager = new ServiceInvokerManager();

            SpringBeanServiceInvoker springBeanServiceInvoker = new SpringBeanServiceInvoker();
            springBeanServiceInvoker.setApplicationContext(getApplicationContext());
            springBeanServiceInvoker.setThreadPoolExecutor(threadPoolExecutor);
            springBeanServiceInvoker.setSagaJsonParser(getSagaJsonParser());
            this.serviceInvokerManager.putServiceInvoker(DomainConstants.SERVICE_TYPE_SPRING_BEAN,
                springBeanServiceInvoker);
        }

        if (this.scriptEngineManager == null) {
            this.scriptEngineManager = new ScriptEngineManager();
        }
    }

    protected ProcessControllerImpl createProcessorController(ProcessCtrlEventPublisher eventPublisher) throws Exception {

        // 创建多个路由器和处理器，用BusinessProcessor包装，放入ProcessController中

        StateMachineProcessRouter stateMachineProcessRouter = new StateMachineProcessRouter();
        stateMachineProcessRouter.initDefaultStateRouters();
        loadStateRouterInterceptors(stateMachineProcessRouter.getStateRouters());

        StateMachineProcessHandler stateMachineProcessHandler = new StateMachineProcessHandler();
        stateMachineProcessHandler.initDefaultHandlers();
        loadStateHandlerInterceptors(stateMachineProcessHandler.getStateHandlers());

        DefaultRouterHandler defaultRouterHandler = new DefaultRouterHandler();
        defaultRouterHandler.setEventPublisher(eventPublisher);

        Map<String, ProcessRouter> processRouterMap = new HashMap<>(1);
        processRouterMap.put(ProcessType.STATE_LANG.getCode(), stateMachineProcessRouter);
        defaultRouterHandler.setProcessRouters(processRouterMap);

        CustomizeBusinessProcessor customizeBusinessProcessor = new CustomizeBusinessProcessor();

        Map<String, ProcessHandler> processHandlerMap = new HashMap<>(1);
        processHandlerMap.put(ProcessType.STATE_LANG.getCode(), stateMachineProcessHandler);
        customizeBusinessProcessor.setProcessHandlers(processHandlerMap);

        Map<String, RouterHandler> routerHandlerMap = new HashMap<>(1);
        routerHandlerMap.put(ProcessType.STATE_LANG.getCode(), defaultRouterHandler);
        customizeBusinessProcessor.setRouterHandlers(routerHandlerMap);

        ProcessControllerImpl processorController = new ProcessControllerImpl();
        processorController.setBusinessProcessor(customizeBusinessProcessor);

        return processorController;
    }

    protected void loadStateHandlerInterceptors(Map<String, StateHandler> stateHandlerMap) {
        for (StateHandler stateHandler : stateHandlerMap.values()) {
            if (stateHandler instanceof InterceptableStateHandler) {
                InterceptableStateHandler interceptableStateHandler = (InterceptableStateHandler) stateHandler;
                List<StateHandlerInterceptor> interceptorList = EnhancedServiceLoader.loadAll(StateHandlerInterceptor.class);
                for (StateHandlerInterceptor interceptor : interceptorList) {
                    if (interceptor.match(interceptableStateHandler.getClass())) {
                        interceptableStateHandler.addInterceptor(interceptor);
                    }

                    if (interceptor instanceof ApplicationContextAware) {
                        ((ApplicationContextAware) interceptor).setApplicationContext(getApplicationContext());
                    }
                }
            }
        }
    }

    protected void loadStateRouterInterceptors(Map<String, StateRouter> stateRouterMap) {
        for (StateRouter stateRouter : stateRouterMap.values()) {
            if (stateRouter instanceof InterceptableStateRouter) {
                InterceptableStateRouter interceptableStateRouter = (InterceptableStateRouter) stateRouter;
                List<StateRouterInterceptor> interceptorList = EnhancedServiceLoader.loadAll(StateRouterInterceptor.class);
                for (StateRouterInterceptor interceptor : interceptorList) {
                    if (interceptor.match(interceptableStateRouter.getClass())) {
                        interceptableStateRouter.addInterceptor(interceptor);
                    }

                    if (interceptor instanceof ApplicationContextAware) {
                        ((ApplicationContextAware) interceptor).setApplicationContext(getApplicationContext());
                    }
                }
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        init();
    }

    @Override
    public StateLogStore getStateLogStore() {
        return this.stateLogStore;
    }

    public void setStateLogStore(StateLogStore stateLogStore) {
        this.stateLogStore = stateLogStore;
    }

    @Override
    public StateLangStore getStateLangStore() {
        return stateLangStore;
    }

    public void setStateLangStore(StateLangStore stateLangStore) {
        this.stateLangStore = stateLangStore;
    }

    @Override
    public ExpressionFactoryManager getExpressionFactoryManager() {
        return this.expressionFactoryManager;
    }

    public void setExpressionFactoryManager(ExpressionFactoryManager expressionFactoryManager) {
        this.expressionFactoryManager = expressionFactoryManager;
    }

    @Override
    public EvaluatorFactoryManager getEvaluatorFactoryManager() {
        return this.evaluatorFactoryManager;
    }

    public void setEvaluatorFactoryManager(EvaluatorFactoryManager evaluatorFactoryManager) {
        this.evaluatorFactoryManager = evaluatorFactoryManager;
    }

    @Override
    public String getCharset() {
        return this.charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    @Override
    public StateMachineRepository getStateMachineRepository() {
        return stateMachineRepository;
    }

    public void setStateMachineRepository(StateMachineRepository stateMachineRepository) {
        this.stateMachineRepository = stateMachineRepository;
    }

    @Override
    public StatusDecisionStrategy getStatusDecisionStrategy() {
        return statusDecisionStrategy;
    }

    public void setStatusDecisionStrategy(StatusDecisionStrategy statusDecisionStrategy) {
        this.statusDecisionStrategy = statusDecisionStrategy;
    }

    @Override
    public SeqGenerator getSeqGenerator() {
        if (seqGenerator == null) {
            synchronized (this) {
                if (seqGenerator == null) {
                    seqGenerator = new SpringJvmUUIDSeqGenerator();
                }
            }
        }
        return seqGenerator;
    }

    public void setSeqGenerator(SeqGenerator seqGenerator) {
        this.seqGenerator = seqGenerator;
    }

    @Override
    public ProcessCtrlEventPublisher getProcessCtrlEventPublisher() {
        return syncProcessCtrlEventPublisher;
    }

    @Override
    public ProcessCtrlEventPublisher getAsyncProcessCtrlEventPublisher() {
        return asyncProcessCtrlEventPublisher;
    }

    public void setAsyncProcessCtrlEventPublisher(ProcessCtrlEventPublisher asyncProcessCtrlEventPublisher) {
        this.asyncProcessCtrlEventPublisher = asyncProcessCtrlEventPublisher;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

    public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
        this.threadPoolExecutor = threadPoolExecutor;
    }

    @Override
    public boolean isEnableAsync() {
        return enableAsync;
    }

    public void setEnableAsync(boolean enableAsync) {
        this.enableAsync = enableAsync;
    }

    @Override
    public StateLogRepository getStateLogRepository() {
        return stateLogRepository;
    }

    public void setStateLogRepository(StateLogRepository stateLogRepository) {
        this.stateLogRepository = stateLogRepository;
    }

    public void setSyncProcessCtrlEventPublisher(ProcessCtrlEventPublisher syncProcessCtrlEventPublisher) {
        this.syncProcessCtrlEventPublisher = syncProcessCtrlEventPublisher;
    }

    public void setResources(Resource[] resources) {
        this.resources = resources;
    }

    @Override
    public ServiceInvokerManager getServiceInvokerManager() {
        return serviceInvokerManager;
    }

    public void setServiceInvokerManager(ServiceInvokerManager serviceInvokerManager) {
        this.serviceInvokerManager = serviceInvokerManager;
    }

    @Override
    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    public int getTransOperationTimeout() {
        return transOperationTimeout;
    }

    public void setTransOperationTimeout(int transOperationTimeout) {
        this.transOperationTimeout = transOperationTimeout;
    }

    @Override
    public int getServiceInvokeTimeout() {
        return serviceInvokeTimeout;
    }

    public void setServiceInvokeTimeout(int serviceInvokeTimeout) {
        this.serviceInvokeTimeout = serviceInvokeTimeout;
    }

    @Override
    public ScriptEngineManager getScriptEngineManager() {
        return scriptEngineManager;
    }

    public void setScriptEngineManager(ScriptEngineManager scriptEngineManager) {
        this.scriptEngineManager = scriptEngineManager;
    }

    public String getSagaJsonParser() {
        return sagaJsonParser;
    }

    public void setSagaJsonParser(String sagaJsonParser) {
        this.sagaJsonParser = sagaJsonParser;
    }

    public boolean isSagaRetryPersistModeUpdate() {
        return sagaRetryPersistModeUpdate;
    }

    public void setSagaRetryPersistModeUpdate(boolean sagaRetryPersistModeUpdate) {
        this.sagaRetryPersistModeUpdate = sagaRetryPersistModeUpdate;
    }

    public boolean isSagaCompensatePersistModeUpdate() {
        return sagaCompensatePersistModeUpdate;
    }

    public void setSagaCompensatePersistModeUpdate(boolean sagaCompensatePersistModeUpdate) {
        this.sagaCompensatePersistModeUpdate = sagaCompensatePersistModeUpdate;
    }
}