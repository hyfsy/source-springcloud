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

package org.springframework.cloud.client.discovery.simple;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * DiscoveryClient implementation defaults to {@link CompositeDiscoveryClient}.
 *
 * @author Biju Kunjummen
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = DiscoveryClientAutoConfigurationDefaultTests.Config.class)
public class DiscoveryClientAutoConfigurationDefaultTests {

	@Autowired
	private DiscoveryClient discoveryClient;

	@Test
	public void simpleDiscoveryClientShouldBeTheDefault() {
		then(this.discoveryClient).isInstanceOf(CompositeDiscoveryClient.class);
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	public static class Config {

	}

}
