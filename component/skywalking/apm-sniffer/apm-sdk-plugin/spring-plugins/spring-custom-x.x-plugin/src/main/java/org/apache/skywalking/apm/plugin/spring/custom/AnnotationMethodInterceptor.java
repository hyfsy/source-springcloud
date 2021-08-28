/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.custom;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @date 2021/06/20
 */
public class AnnotationMethodInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog LOG = LogManager.getLogger(AnnotationConstructorInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        LOG.info("==========Invoke beforeMethod==========");
        LOG.info("Enhanced class: " + objInst.getClass());
        LOG.info("SkyWalkingDynamicField: " + objInst.getSkyWalkingDynamicField());
        LOG.info("All Arguments: " + Arrays.toString(allArguments));
        LOG.info("Argument Types: " + Arrays.toString(argumentsTypes));
        LOG.info("==================End==================");
        method.invoke(objInst, allArguments);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        LOG.info("afterMethod invoked.");
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        LOG.info("handleMethodException: " + method);
    }
}
