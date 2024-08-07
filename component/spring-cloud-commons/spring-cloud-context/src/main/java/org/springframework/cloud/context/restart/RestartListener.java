/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.cloud.context.restart;

import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * A listener that stores enough information about an application, as it starts, to be
 * able to restart it later if needed.
 *
 * @author Dave Syer
 *
 */
public class RestartListener implements SmartApplicationListener {

	private ConfigurableApplicationContext context;

	private ApplicationPreparedEvent event;

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationPreparedEvent.class.isAssignableFrom(eventType)
				|| ContextRefreshedEvent.class.isAssignableFrom(eventType)
				|| ContextClosedEvent.class.isAssignableFrom(eventType);

	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent input) {
		if (input instanceof ApplicationPreparedEvent) {
			this.event = (ApplicationPreparedEvent) input;
			if (this.context == null) {
				this.context = this.event.getApplicationContext();
			}
		}
		else if (input instanceof ContextRefreshedEvent) {
			if (this.context != null && input.getSource().equals(this.context) && this.event != null) {
				// 发布的是 ApplicationPreparedEvent 事件
				// @see class doc
				this.context.publishEvent(this.event);
			}
		}
		else {
			if (this.context != null && input.getSource().equals(this.context)) {
				this.context = null;
				this.event = null;
			}
		}
	}

}
