package com.hyf.cloud.context.annotation;

/**
 * @author baB_hyf
 * @date 2021/05/14
 */
public @interface InstanceAnnotation {
	String name() default "111";
}
