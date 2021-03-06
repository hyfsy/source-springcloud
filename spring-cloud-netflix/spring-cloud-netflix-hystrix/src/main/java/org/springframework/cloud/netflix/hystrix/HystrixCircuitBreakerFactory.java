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

package org.springframework.cloud.netflix.hystrix;

import java.util.function.Function;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.util.Assert;

/**
 * Builds Hystrix circuit breakers.
 *
 * @author Ryan Baxter
 */
public class HystrixCircuitBreakerFactory extends
		CircuitBreakerFactory<HystrixCommand.Setter, HystrixCircuitBreakerFactory.HystrixConfigBuilder> {

	// 默认配置
	private Function<String, HystrixCommand.Setter> defaultConfiguration = id -> HystrixCommand.Setter
			.withGroupKey(
					HystrixCommandGroupKey.Factory.asKey(getClass().getSimpleName()))
			.andCommandKey(HystrixCommandKey.Factory.asKey(id));

	public void configureDefault(
			Function<String, HystrixCommand.Setter> defaultConfiguration) {
		this.defaultConfiguration = defaultConfiguration;
	}

	// 获取配置
	public HystrixConfigBuilder configBuilder(String id) {
		return new HystrixConfigBuilder(id);
	}

	// 获取 CircuitBreaker 对象
	public HystrixCircuitBreaker create(String id) {
		Assert.hasText(id, "A CircuitBreaker must have an id.");
		HystrixCommand.Setter setter = getConfigurations().computeIfAbsent(id,
				defaultConfiguration);
		// 创建断路器对象
		return new HystrixCircuitBreaker(setter);
	}

	public static class HystrixConfigBuilder
			extends AbstractHystrixConfigBuilder<HystrixCommand.Setter> {

		public HystrixConfigBuilder(String id) {
			super(id);
		}

		// 基本配置
		@Override
		public HystrixCommand.Setter build() {
			return HystrixCommand.Setter.withGroupKey(getGroupKey())
					.andCommandKey(getCommandKey())
					.andCommandPropertiesDefaults(getCommandPropertiesSetter());
		}

	}

}
