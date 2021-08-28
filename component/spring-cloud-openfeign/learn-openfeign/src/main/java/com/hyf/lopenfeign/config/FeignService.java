package com.hyf.lopenfeign.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author baB_hyf
 * @date 2021/05/11
 */
@Component
@FeignClient("http://localhost:8304/feign")
public interface FeignService {

	@RequestMapping("/hello")
	String hello();
}
