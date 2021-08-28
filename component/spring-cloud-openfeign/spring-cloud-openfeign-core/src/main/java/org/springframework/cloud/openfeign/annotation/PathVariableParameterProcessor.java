/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import feign.MethodMetadata;

import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.web.bind.annotation.PathVariable;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * {@link PathVariable} parameter processor.
 *
 * @author Jakub Narloch
 * @author Abhijit Sarkar
 * @see AnnotatedParameterProcessor
 */
public class PathVariableParameterProcessor implements AnnotatedParameterProcessor {

	private static final Class<PathVariable> ANNOTATION = PathVariable.class;

	@Override
	public Class<? extends Annotation> getAnnotationType() {
		return ANNOTATION;
	}

	@Override
	public boolean processArgument(AnnotatedParameterContext context,
			Annotation annotation, Method method) {
		String name = ANNOTATION.cast(annotation).value();
		checkState(emptyToNull(name) != null,
				"PathVariable annotation was empty on param %s.",
				context.getParameterIndex());
		context.setParameterName(name);

		MethodMetadata data = context.getMethodMetadata();
		String varName = '{' + name + '}'; // {value}
		if (!data.template().url().contains(varName) // uri中没有
				&& !searchMapValues(data.template().queries(), varName) // 已解析的查询条件中没有
				&& !searchMapValues(data.template().headers(), varName)) { // 已解析的Header中没有
			data.formParams().add(name); // 添加到form参数中
		}
		return true;
	}

	private <K, V> boolean searchMapValues(Map<K, Collection<V>> map, V search) {
		Collection<Collection<V>> values = map.values();
		if (values == null) {
			return false;
		}
		for (Collection<V> entry : values) {
			if (entry.contains(search)) {
				return true;
			}
		}
		return false;
	}

}
