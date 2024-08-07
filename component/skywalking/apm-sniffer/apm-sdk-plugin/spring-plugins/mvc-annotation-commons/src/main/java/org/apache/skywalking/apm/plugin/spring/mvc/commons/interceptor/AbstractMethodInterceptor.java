/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.RequestHolder;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.ResponseHolder;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.ReactiveResponseHolder;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.SpringMVCPluginConfig;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.exception.IllegalMethodStackDepthException;
import org.apache.skywalking.apm.plugin.spring.mvc.commons.exception.ServletResponseNotFoundException;
import org.apache.skywalking.apm.util.StringUtil;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.CONTROLLER_METHOD_STACK_DEPTH;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.FORWARD_REQUEST_FLAG;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

/**
 * the abstract method interceptor
 */
public abstract class AbstractMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static boolean IS_SERVLET_GET_STATUS_METHOD_EXIST;
    private static final String SERVLET_RESPONSE_CLASS = "javax.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    static {
        IS_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(
            AbstractMethodInterceptor.class.getClassLoader(), SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
    }

    public abstract String getRequestURL(Method method);

    public abstract String getAcceptedMethodTypes(Method method);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {

        Boolean forwardRequestFlag = (Boolean) ContextManager.getRuntimeContext().get(FORWARD_REQUEST_FLAG);
        /**
         * Spring MVC plugin do nothing if current request is forward request.
         * Ref: https://github.com/apache/skywalking/pull/1325
         */
        if (forwardRequestFlag != null && forwardRequestFlag) {
            return;
        }

        // 端点名称
        String operationName;

        // 端点名唯一
        if (SpringMVCPluginConfig.Plugin.SpringMVC.USE_QUALIFIED_NAME_AS_ENDPOINT_NAME) {
            operationName = MethodUtil.generateOperationName(method);
        } else {
            // 构造器拦截点中自己设置的 see InstanceConstructorInterceptor
            EnhanceRequireObjectCache pathMappingCache = (EnhanceRequireObjectCache) objInst.getSkyWalkingDynamicField();
            // 缓存请求URI
            String requestURL = pathMappingCache.findPathMapping(method);
            if (requestURL == null) {
                // 注解中查找
                requestURL = getRequestURL(method);
                pathMappingCache.addPathMapping(method, requestURL);
                requestURL = pathMappingCache.findPathMapping(method);
            }
            operationName = getAcceptedMethodTypes(method) + requestURL;
        }

        // 获取当前请求对象
        RequestHolder request = (RequestHolder) ContextManager.getRuntimeContext()
                                                              .get(REQUEST_KEY_IN_RUNTIME_CONTEXT);
        if (request != null) {
            // 用于判断当前segment是否结束
            StackDepth stackDepth = (StackDepth) ContextManager.getRuntimeContext().get(CONTROLLER_METHOD_STACK_DEPTH);

            // 当前节点的第一次请求，需要还原追踪信息
            if (stackDepth == null) {
                // 上下文载体，包含追踪的所有信息
                ContextCarrier contextCarrier = new ContextCarrier();
                CarrierItem next = contextCarrier.items();
                // 从当前请求的请求头中还原追踪信息
                while (next.hasNext()) {
                    next = next.next();
                    next.setHeadValue(request.getHeader(next.getHeadKey()));
                }

                // 创建当前span，第一次进入，创建 Entry 类型的
                AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
                // 添加标签
                Tags.URL.set(span, request.requestURL());
                Tags.HTTP.METHOD.set(span, request.requestMethod());
                span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
                SpanLayer.asHttp(span);

                // 第一次才添加

                // 添加请求参数
                if (SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS) {
                    collectHttpParam(request, span);
                }

                // 添加请求头
                if (!CollectionUtil.isEmpty(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS)) {
                    collectHttpHeaders(request, span);
                }

                stackDepth = new StackDepth();
                ContextManager.getRuntimeContext().put(CONTROLLER_METHOD_STACK_DEPTH, stackDepth);
            } else {
                // 后续进入，创建 Local 类型的
                AbstractSpan span = ContextManager.createLocalSpan(buildOperationName(objInst, method));
                span.setComponent(ComponentsDefine.SPRING_MVC_ANNOTATION);
            }

            stackDepth.increment();
        }
    }

    private String buildOperationName(Object invoker, Method method) {
        StringBuilder operationName = new StringBuilder(invoker.getClass().getName()).append(".")
                                                                                     .append(method.getName())
                                                                                     .append("(");
        for (Class<?> type : method.getParameterTypes()) {
            operationName.append(type.getName()).append(",");
        }

        if (method.getParameterTypes().length > 0) {
            operationName = operationName.deleteCharAt(operationName.length() - 1);
        }

        return operationName.append(")").toString();
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Boolean forwardRequestFlag = (Boolean) ContextManager.getRuntimeContext().get(FORWARD_REQUEST_FLAG);
        /**
         * Spring MVC plugin do nothing if current request is forward request.
         * Ref: https://github.com/apache/skywalking/pull/1325
         */
        if (forwardRequestFlag != null && forwardRequestFlag) {
            return ret;
        }

        RequestHolder request = (RequestHolder) ContextManager.getRuntimeContext()
                                                              .get(REQUEST_KEY_IN_RUNTIME_CONTEXT);

        if (request != null) {
            StackDepth stackDepth = (StackDepth) ContextManager.getRuntimeContext().get(CONTROLLER_METHOD_STACK_DEPTH);
            if (stackDepth == null) {
                throw new IllegalMethodStackDepthException();
            } else {
                stackDepth.decrement();
            }

            AbstractSpan span = ContextManager.activeSpan();

            // 追踪结束
            if (stackDepth.depth() == 0) {
                ResponseHolder response = (ResponseHolder) ContextManager.getRuntimeContext()
                                                                         .get(
                                                                             RESPONSE_KEY_IN_RUNTIME_CONTEXT);
                if (response == null) {
                    throw new ServletResponseNotFoundException();
                }

                if (IS_SERVLET_GET_STATUS_METHOD_EXIST && response.statusCode() >= 400) {
                    // 标记错误
                    span.errorOccurred();
                    Tags.STATUS_CODE.set(span, Integer.toString(response.statusCode()));
                }
                if (response instanceof ReactiveResponseHolder) {
                    ReactiveResponseHolder reactiveResponse = (ReactiveResponseHolder) response;
                    AbstractSpan async = span.prepareForAsync();
                    reactiveResponse.setSpan(async);
                }
                ContextManager.getRuntimeContext().remove(REQUEST_KEY_IN_RUNTIME_CONTEXT);
                ContextManager.getRuntimeContext().remove(RESPONSE_KEY_IN_RUNTIME_CONTEXT);
                ContextManager.getRuntimeContext().remove(CONTROLLER_METHOD_STACK_DEPTH);
            }

            // Active HTTP parameter collection automatically in the profiling context.
            if (!SpringMVCPluginConfig.Plugin.SpringMVC.COLLECT_HTTP_PARAMS && span.isProfiling()) {
                collectHttpParam(request, span);
            }

            // 归档 span，放入segment中，必要时结束segment
            ContextManager.stopSpan();
        }

        return ret;
    }

    // 异常处理就是打日志
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }

    // 将请求参数放入标签中
    private void collectHttpParam(RequestHolder request, AbstractSpan span) {
        final Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            String tagValue = CollectionUtil.toString(parameterMap);
            tagValue = SpringMVCPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                StringUtil.cut(tagValue, SpringMVCPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) : tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }

    // 将请求头信息放入标签中
    private void collectHttpHeaders(RequestHolder request, AbstractSpan span) {
        final List<String> headersList = new ArrayList<>(SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS.size());
        SpringMVCPluginConfig.Plugin.Http.INCLUDE_HTTP_HEADERS.stream()
                // 过滤掉没有值的头
                                                              .filter(
                                                                  headerName -> request.getHeaders(headerName) != null)
                                                              .forEach(headerName -> {
                                                                  Enumeration<String> headerValues = request.getHeaders(
                                                                      headerName);
                                                                  List<String> valueList = Collections.list(
                                                                      headerValues);
                                                                  if (!CollectionUtil.isEmpty(valueList)) {
                                                                      String headerValue = valueList.toString();
                                                                      headersList.add(headerName + "=" + headerValue);
                                                                  }
                                                              });

        if (!headersList.isEmpty()) {
            String tagValue = headersList.stream().collect(Collectors.joining("\n"));
            tagValue = SpringMVCPluginConfig.Plugin.Http.HTTP_HEADERS_LENGTH_THRESHOLD > 0 ?
                StringUtil.cut(tagValue, SpringMVCPluginConfig.Plugin.Http.HTTP_HEADERS_LENGTH_THRESHOLD) : tagValue;
            Tags.HTTP.HEADERS.set(span, tagValue);
        }
    }
}
