package com.hyf.cloud.loadbalancer;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

/**
 * @author baB_hyf
 * @date 2021/05/17
 */
public class ServiceInstanceSupplierBuilderTest {

	public static void main(String[] args) {
		ServiceInstanceListSupplier.builder()

				// baseCreator

				.withBase(null)
				// .withDiscoveryClient() // reactive
				.withBlockingDiscoveryClient()
				// .withHealthChecks() // WebClient
				.withBlockingHealthChecks() // RestTemplate

				// delegateCreator

				.withZonePreference()
				.withSameInstancePreference()
				.withRequestBasedStickySession()
				.withHints()
				.withRetryAwareness()

				// cachingCreator

				.withCaching()
				.build(null);
	}
}
