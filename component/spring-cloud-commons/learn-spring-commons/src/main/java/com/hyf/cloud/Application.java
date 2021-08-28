package com.hyf.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author baB_hyf
 * @date 2021/05/14
 */
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		System.setProperty("spring.cloud.bootstrap.name", "abc"); // 重置配置文件名称，暂时只能采用此方法

		SpringApplication.run(Application.class, args);
	}
}
