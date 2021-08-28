/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.autoconfigure;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.context.properties.ConfigurationPropertiesBeans;
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for {@link ConfigurationPropertiesRebinder}.
 *
 * 重新绑定 @ConfigurationProperties 的配置的自动装配
 *
 * 该对象在spring.factories中存在两个相同的，因为默认的应用程序和BootstrapContext都需要
 *
 * @author Dave Syer
 */
@Configuration(proxyBeanMethods = false)
// SpringBoot的属性绑定实例化（@ConfigurationProperties对象）
@ConditionalOnBean(ConfigurationPropertiesBindingPostProcessor.class)
public class ConfigurationPropertiesRebinderAutoConfiguration
		implements ApplicationContextAware, SmartInitializingSingleton {

	private ApplicationContext context;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.context = applicationContext;
	}

	/** 绑定属性集合 */
	@Bean
	@ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
	public static ConfigurationPropertiesBeans configurationPropertiesBeans() {
		return new ConfigurationPropertiesBeans();
	}

	/** 属性绑定器，处理属性的重新绑定 */
	@Bean
	@ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
	public ConfigurationPropertiesRebinder configurationPropertiesRebinder(ConfigurationPropertiesBeans beans) {
		ConfigurationPropertiesRebinder rebinder = new ConfigurationPropertiesRebinder(beans);
		return rebinder;
	}

	/** 重新绑定父容器属性 */
	@Override
	public void afterSingletonsInstantiated() {
		// After all beans are initialized explicitly rebind beans from the parent
		// so that changes during the initialization of the current context are
		// reflected. In particular this can be important when low level services like
		// decryption are bootstrapped in the parent, but need to change their
		// configuration before the child context is processed.
		if (this.context.getParent() != null) {
			// TODO: make this optional? (E.g. when creating child contexts that prefer to
			// be isolated.)
			ConfigurationPropertiesRebinder rebinder = this.context.getBean(ConfigurationPropertiesRebinder.class);
			for (String name : this.context.getParent().getBeanDefinitionNames()) {
				rebinder.rebind(name);
			}
		}
	}

}