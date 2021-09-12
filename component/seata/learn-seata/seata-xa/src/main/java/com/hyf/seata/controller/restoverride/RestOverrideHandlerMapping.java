package com.hyf.seata.controller.restoverride;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接口的类或方法上注释 {@link RestOverride} 来决定是否覆盖原有的接口
 * <p>or
 * <p>接口的类上注释 {@link org.springframework.context.annotation.Primary} 来决定是否覆盖原有的接口
 * <p>没有注释接口时，如果存在url相同的接口则具体路由到哪个是不确定的（根据文件名判断）
 *
 * @author baB_hyf
 * @date 2021/01/08
 */
public class RestOverrideHandlerMapping extends RequestMappingHandlerMapping {

	// TODO 可以改成一个，当初的想法是可能需要bean名称
	private final Map<String, Object>             handlerMethods = new LinkedHashMap<>();
	private final Map<String, RequestMappingInfo> mappingInfos   = new LinkedHashMap<>();

	@Override
	protected void registerHandlerMethod(Object handler, Method method, RequestMappingInfo mapping) {
		PatternsRequestCondition patternsCondition = mapping.getPatternsCondition();
		for (String path : patternsCondition.getPatterns()) {
			// 1. 无重复直接注册
			if (!this.handlerMethods.containsKey(path)) {
				this.handlerMethods.put(path, handler);
				this.mappingInfos.put(path, mapping);
			}
			// 2. 重复则只处理String类型的handler
			else if (!(handler instanceof String)) {
				throw new IllegalStateException("Cannot deal non-string ambiguous mapping: " + path);
			}
			// 3. 判断是否覆盖已存在的HandlerMethod
			else {
				if (!canOverride(path, handler, method, mapping)) {
					return;
				}

				// 覆盖
				super.unregisterMapping(this.mappingInfos.remove(path));
				this.handlerMethods.put(path, handler);
				this.mappingInfos.put(path, mapping);
			}
		}

		// 注册
		super.registerHandlerMethod(handler, method, mapping);
	}

	@Override
	protected void handlerMethodsInitialized(Map<RequestMappingInfo, HandlerMethod> handlerMethods) {
		// 防止jrebel热刷新出现注册不了的情况
		this.handlerMethods.clear();
		this.mappingInfos.clear();
	}

	/**
	 * 判断已有的HandlerMethod是否可以被覆盖
	 *
	 * @param path    rest接口路径
	 * @param handler bean名称
	 * @param method  新的方法对象
	 * @param mapping 映射信息
	 * @return true表示覆盖已有的HandlerMethod，否则返回false
	 */
	protected boolean canOverride(String path, Object handler, Method method, RequestMappingInfo mapping) {
		RestOverride methodAnnotation = AnnotationUtils.findAnnotation(method, RestOverride.class);
		if (methodAnnotation != null) {
			return true;
		}

		RestOverride classAnnotation = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RestOverride.class);
		if (classAnnotation != null) {
			return true;
		}

		// DefaultListableBeanFactory factory = (DefaultListableBeanFactory) super.getApplicationContext().getAutowireCapableBeanFactory();
		// BeanDefinition candidate = factory.getBeanDefinition((String) handler);
		// return candidate.isPrimary();
        return false;
	}
}
