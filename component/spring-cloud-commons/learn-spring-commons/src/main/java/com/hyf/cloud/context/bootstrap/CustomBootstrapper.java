package com.hyf.cloud.context.bootstrap;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.BootstrapContext;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.Bootstrapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * 注册BootstrapContext对象给应用上下文使用
 *
 * @author baB_hyf
 * @date 2021/05/15
 */
public class CustomBootstrapper implements Bootstrapper {

	@Override
	public void intitialize(BootstrapRegistry registry) {

		// 容器完全初始化完毕前提供的一种共享的对象注册机制
		registry.registerIfAbsent(InnerPerson.class, context -> new InnerPerson());

		// 转换为SpringBean
		registry.addCloseListener(event -> {
			BootstrapContext bootstrapContext = event.getBootstrapContext();
			ConfigurableApplicationContext applicationContext = event.getApplicationContext();

			ApplicationStartup applicationStartup = applicationContext.getApplicationStartup();
			System.out.println(applicationStartup);

			InnerPerson innerPerson = bootstrapContext.get(InnerPerson.class);
			ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
			beanFactory.registerSingleton("innerPerson", innerPerson);
		});
	}

	static class InnerPerson {

		public InnerPerson() {
			System.out.println("Bootstrapper success");
		}
	}

	@Component
	static class StaticInnerPerson {

		@Resource
		private InnerPerson innerPerson;

		@PostConstruct
		public void a() {
			System.out.println("InnerPerson: " + innerPerson);
		}

	}
}
