/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.openfeign;

import java.util.ArrayList;
import java.util.List;

import com.netflix.hystrix.HystrixCommand;
import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.form.MultipartFormContentProcessor;
import feign.form.spring.SpringFormEncoder;
import feign.hystrix.HystrixFeign;
import feign.optionals.OptionalDecoder;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.cloud.openfeign.support.AbstractFormWriter;
import org.springframework.cloud.openfeign.support.FeignEncoderProperties;
import org.springframework.cloud.openfeign.support.PageableSpringEncoder;
import org.springframework.cloud.openfeign.support.PageableSpringQueryMapEncoder;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;

import static feign.form.ContentType.MULTIPART;

/**
 * @author Dave Syer
 * @author Venil Noronha
 * @author Darren Foong
 * @author Olga Maciaszek-Sharma
 * @author Hyeonmin Park
 */
@Configuration(proxyBeanMethods = false)
public class FeignClientsConfiguration {

	/** 消息转换器 */
	@Autowired
	private ObjectFactory<HttpMessageConverters> messageConverters;

	/** 接口方法上更多的注解处理 */
	@Autowired(required = false)
	private List<AnnotatedParameterProcessor> parameterProcessors = new ArrayList<>();

	/** 添加 Feign特殊的格式处理 */
	@Autowired(required = false)
	private List<FeignFormatterRegistrar> feignFormatterRegistrars = new ArrayList<>();

	/** Feign日志实现 */
	@Autowired(required = false)
	private Logger logger;

	/** 分页及排序的配置属性 */
	@Autowired(required = false)
	private SpringDataWebProperties springDataWebProperties;

	// Feign 配置属性

	@Autowired(required = false)
	private FeignClientProperties feignClientProperties;

	@Autowired(required = false)
	private FeignEncoderProperties encoderProperties;

	// 编解码支持

	@Bean
	@ConditionalOnMissingBean
	public Decoder feignDecoder() {
		return new OptionalDecoder(
				new ResponseEntityDecoder(new SpringDecoder(this.messageConverters)));
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnMissingClass("org.springframework.data.domain.Pageable")
	public Encoder feignEncoder(ObjectProvider<AbstractFormWriter> formWriterProvider) {
		return springEncoder(formWriterProvider, encoderProperties);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.domain.Pageable")
	@ConditionalOnMissingBean
	public Encoder feignEncoderPageable(
			ObjectProvider<AbstractFormWriter> formWriterProvider) {
		PageableSpringEncoder encoder = new PageableSpringEncoder(
				springEncoder(formWriterProvider, encoderProperties));

		if (springDataWebProperties != null) {
			encoder.setPageParameter(
					springDataWebProperties.getPageable().getPageParameter());
			encoder.setSizeParameter(
					springDataWebProperties.getPageable().getSizeParameter());
			encoder.setSortParameter(
					springDataWebProperties.getSort().getSortParameter());
		}
		return encoder;
	}

	/** 分页查询及排序的对象序列化支持 */
	@Bean
	@ConditionalOnClass(name = "org.springframework.data.domain.Pageable")
	@ConditionalOnMissingBean
	public QueryMapEncoder feignQueryMapEncoderPageable() {
		return new PageableSpringQueryMapEncoder();
	}

	/** 从类中提取所有方法元数据，用于解析service接口 */
	@Bean
	@ConditionalOnMissingBean
	public Contract feignContract(ConversionService feignConversionService) {
		boolean decodeSlash = feignClientProperties == null
				|| feignClientProperties.isDecodeSlash();
		return new SpringMvcContract(this.parameterProcessors, feignConversionService,
				decodeSlash);
	}

	/** Feign特殊的转换服务 */
	@Bean
	public FormattingConversionService feignConversionService() {
		FormattingConversionService conversionService = new DefaultFormattingConversionService();
		for (FeignFormatterRegistrar feignFormatterRegistrar : this.feignFormatterRegistrars) {
			feignFormatterRegistrar.registerFormatters(conversionService);
		}
		return conversionService;
	}

	/** 默认不重试 */
	@Bean
	@ConditionalOnMissingBean
	public Retryer feignRetryer() {
		return Retryer.NEVER_RETRY;
	}

	/** 使用默认的 Feign.Builder */
	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	public Feign.Builder feignBuilder(Retryer retryer) {
		return Feign.builder().retryer(retryer);
	}

	/** Feign的日志工厂 */
	@Bean
	@ConditionalOnMissingBean(FeignLoggerFactory.class)
	public FeignLoggerFactory feignLoggerFactory() {
		return new DefaultFeignLoggerFactory(this.logger);
	}

	/** FeignClient额外配置 */
	@Bean
	@ConditionalOnMissingBean(FeignClientConfigurer.class)
	public FeignClientConfigurer feignClientConfigurer() {
		return new FeignClientConfigurer() {
		};
	}

	private Encoder springEncoder(ObjectProvider<AbstractFormWriter> formWriterProvider,
			FeignEncoderProperties encoderProperties) {
		AbstractFormWriter formWriter = formWriterProvider.getIfAvailable();

		if (formWriter != null) {
			return new SpringEncoder(new SpringPojoFormEncoder(formWriter),
					this.messageConverters, encoderProperties);
		}
		else {
			return new SpringEncoder(new SpringFormEncoder(), this.messageConverters,
					encoderProperties);
		}
	}

	/** Hystrix类型的 Builder支持 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ HystrixCommand.class, HystrixFeign.class })
	protected static class HystrixFeignConfiguration {

		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean
		@ConditionalOnProperty(name = "feign.hystrix.enabled")
		public Feign.Builder feignHystrixBuilder() {
			return HystrixFeign.builder();
		}

	}

	private class SpringPojoFormEncoder extends SpringFormEncoder {

		SpringPojoFormEncoder(AbstractFormWriter formWriter) {
			super();

			MultipartFormContentProcessor processor = (MultipartFormContentProcessor) getContentProcessor(
					MULTIPART);
			processor.addFirstWriter(formWriter);
		}

	}

	/** circuit-breaker类型的 Builder支持 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(CircuitBreaker.class)
	@ConditionalOnProperty("feign.circuitbreaker.enabled")
	protected static class CircuitBreakerPresentFeignBuilderConfiguration {

		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean({ Feign.Builder.class, CircuitBreakerFactory.class })
		public Feign.Builder defaultFeignBuilder(Retryer retryer) {
			return Feign.builder().retryer(retryer);
		}

		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean
		@ConditionalOnBean(CircuitBreakerFactory.class)
		public Feign.Builder circuitBreakerFeignBuilder() {
			return FeignCircuitBreaker.builder();
		}

	}

}
