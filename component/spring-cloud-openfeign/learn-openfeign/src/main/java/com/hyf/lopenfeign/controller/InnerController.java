package com.hyf.lopenfeign.controller;

import com.hyf.lopenfeign.config.FeignService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author baB_hyf
 * @date 2021/05/11
 */
@RestController
public class InnerController {

	@Resource
	private FeignService feignService;

	@RequestMapping("invoke")
	public String invoke() {
		return feignService.hello();
	}

	@RequestMapping("hello")
	public String hello() {
		return "hello";
	}
}
