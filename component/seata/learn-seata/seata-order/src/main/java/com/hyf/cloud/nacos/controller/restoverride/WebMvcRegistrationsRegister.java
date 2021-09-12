package com.hyf.cloud.nacos.controller.restoverride;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * @author baB_hyf
 * @date 2021/01/08
 */
@Component
public class WebMvcRegistrationsRegister implements WebMvcRegistrations {

	@Override
	public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
		return new RestOverrideHandlerMapping();
	}
}
