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

package org.springframework.cloud.openfeign.encoding;

import feign.Feign;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Feign request compression.
 *
 * @author Jakub Narloch
 * @see FeignContentGzipEncodingInterceptor
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FeignClientEncodingProperties.class)
@ConditionalOnClass(Feign.class)
// The OK HTTP client uses "transparent" compression.
// If the content-encoding header is present it disable transparent compression
@ConditionalOnMissingBean(type = "okhttp3.OkHttpClient")
@ConditionalOnProperty("feign.compression.request.enabled")
@AutoConfigureAfter(FeignAutoConfiguration.class)
public class FeignContentGzipEncodingAutoConfiguration {

	// 表示请求压缩，需要添加 Content-Encoding 头
	// 会先校验 Context-Length 必须大于请求最小阈值，并且 Context-Type 包含在 指定的mimetype中
	@Bean
	public FeignContentGzipEncodingInterceptor feignContentGzipEncodingInterceptor(
			FeignClientEncodingProperties properties) {
		return new FeignContentGzipEncodingInterceptor(properties);
	}

}