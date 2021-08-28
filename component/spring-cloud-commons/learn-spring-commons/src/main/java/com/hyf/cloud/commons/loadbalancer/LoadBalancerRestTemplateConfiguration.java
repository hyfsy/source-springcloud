package com.hyf.cloud.commons.loadbalancer;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author baB_hyf
 * @date 2021/05/16
 */
@Configuration
public class LoadBalancerRestTemplateConfiguration {

	@Bean
	@LoadBalanced // TODO ?
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
