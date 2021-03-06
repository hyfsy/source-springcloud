/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.eureka;

import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;

/**
 * 扩展实例配置，增加安全端口配置和初始化的实例状态配置
 *
 * @author Spencer Gibb
 */
public interface CloudEurekaInstanceConfig extends EurekaInstanceConfig {

	void setNonSecurePort(int port);

	void setSecurePort(int securePort);

	InstanceInfo.InstanceStatus getInitialStatus();

}
