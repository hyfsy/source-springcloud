package com.hyf.cloud.context.bootstrap;

import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.bootstrap.BootstrapConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 不推荐放在 @ComponentScan 能扫描到的路径下
 *
 * @author baB_hyf
 * @date 2021/05/15
 */
@BootstrapConfiguration(exclude = RefreshAutoConfiguration.class) // exclude is invalid
public class BootstrapConfigurationTest {

	@Bean
	public Person person() {
		return new Person();
	}

	static class Person {
		public Person() {
			System.out.println("BootstrapConfiguration success!");
		}
	}
}
