package com.hyf.cloud.context.annotation;

import java.lang.annotation.Annotation;

/**
 * @author baB_hyf
 * @date 2021/05/14
 */
public class CreateAnnotationTest {

	public static void main(String[] args) {
		InstanceAnnotation instanceAnnotation = new InstanceAnnotation() {

			@Override
			public Class<? extends Annotation> annotationType() {
				return InstanceAnnotation.class;
			}

			@Override
			public String name() {
				return null;
			}
		};

		System.out.println(instanceAnnotation);
	}
}
