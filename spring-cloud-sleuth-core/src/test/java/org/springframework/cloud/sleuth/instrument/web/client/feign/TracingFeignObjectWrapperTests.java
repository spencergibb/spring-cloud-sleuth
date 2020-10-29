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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import feign.Client;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.loadbalancer.RetryableFeignBlockingLoadBalancerClient;

import static org.mockito.Mockito.mock;

/**
 * @author Marcin Grzejszczak
 * @author Olga Maciaszek-Sharma
 */
@ExtendWith(MockitoExtension.class)
public class TracingFeignObjectWrapperTests {

	@Mock
	BeanFactory beanFactory;

	@InjectMocks
	TraceFeignObjectWrapper traceFeignObjectWrapper;

	@Test
	public void should_wrap_a_client_into_lazy_trace_client() {
		BDDAssertions.then(this.traceFeignObjectWrapper.wrap(mock(Client.class)))
				.isExactlyInstanceOf(LazyTracingFeignClient.class);
	}

	@Test
	public void should_not_wrap_a_bean_that_is_not_feign_related() {
		String notFeignRelatedObject = "object";
		BDDAssertions.then(this.traceFeignObjectWrapper.wrap(notFeignRelatedObject)).isSameAs(notFeignRelatedObject);
	}

	// gh-1528
	@Test
	public void should_wrap_feign_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(BlockingLoadBalancerClient.class);
		LoadBalancerClientFactory loadBalancerClientFactory = mock(LoadBalancerClientFactory.class);
		Mockito.when(beanFactory.getBean(LoadBalancerClient.class)).thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper.wrap(new FeignBlockingLoadBalancerClient(delegate, loadBalancerClient,
				new LoadBalancerProperties(), loadBalancerClientFactory));

		Assertions.assertThat(wrapped).isInstanceOf(TraceFeignBlockingLoadBalancerClient.class);
	}

	// gh-1528
	@Test
	public void should_wrap_feign_retryable_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(BlockingLoadBalancerClient.class);
		LoadBalancerClientFactory loadBalancerClientFactory = mock(LoadBalancerClientFactory.class);
		LoadBalancedRetryFactory retryFactory = mock(LoadBalancedRetryFactory.class);
		Mockito.when(beanFactory.getBean(LoadBalancerClient.class)).thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper.wrap(new RetryableFeignBlockingLoadBalancerClient(delegate,
				loadBalancerClient, retryFactory, new LoadBalancerProperties(), loadBalancerClientFactory));

		Assertions.assertThat(wrapped).isInstanceOf(TraceRetryableFeignBlockingLoadBalancerClient.class);
	}

	// gh-1528, gh-1125
	@Test
	public void should_wrap_subclass_of_feign_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(BlockingLoadBalancerClient.class);
		LoadBalancerClientFactory loadBalancerClientFactory = mock(LoadBalancerClientFactory.class);
		Mockito.when(beanFactory.getBean(LoadBalancerClient.class)).thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper.wrap(
				new org.springframework.cloud.sleuth.instrument.web.client.feign.TracingFeignObjectWrapperTests.TestFeignBlockingLoadBalancerClient(
						delegate, loadBalancerClient, loadBalancerClientFactory));

		Assertions.assertThat(wrapped).isInstanceOf(TraceFeignBlockingLoadBalancerClient.class);
	}

	// gh-1528, gh-1125
	@Test
	public void should_wrap_subclass_of_retryable_feign_loadbalancer_client() {
		Client delegate = mock(Client.class);
		BlockingLoadBalancerClient loadBalancerClient = mock(BlockingLoadBalancerClient.class);
		LoadBalancerClientFactory loadBalancerClientFactory = mock(LoadBalancerClientFactory.class);
		LoadBalancedRetryFactory retryFactory = mock(LoadBalancedRetryFactory.class);
		Mockito.when(beanFactory.getBean(LoadBalancerClient.class)).thenReturn(loadBalancerClient);

		Object wrapped = traceFeignObjectWrapper.wrap(
				new org.springframework.cloud.sleuth.instrument.web.client.feign.TracingFeignObjectWrapperTests.TestRetryableFeignBlockingLoadBalancerClient(
						delegate, loadBalancerClient, retryFactory, loadBalancerClientFactory));

		Assertions.assertThat(wrapped).isInstanceOf(TraceRetryableFeignBlockingLoadBalancerClient.class);
	}

	static class TestFeignBlockingLoadBalancerClient extends FeignBlockingLoadBalancerClient {

		TestFeignBlockingLoadBalancerClient(Client delegate, BlockingLoadBalancerClient loadBalancerClient,
				LoadBalancerClientFactory loadBalancerClientFactory) {
			super(delegate, loadBalancerClient, new LoadBalancerProperties(), loadBalancerClientFactory);
		}

	}

	static class TestRetryableFeignBlockingLoadBalancerClient extends RetryableFeignBlockingLoadBalancerClient {

		TestRetryableFeignBlockingLoadBalancerClient(Client delegate, BlockingLoadBalancerClient loadBalancerClient,
				LoadBalancedRetryFactory retryFactory, LoadBalancerClientFactory loadBalancerClientFactory) {
			super(delegate, loadBalancerClient, retryFactory, new LoadBalancerProperties(), loadBalancerClientFactory);
		}

	}

}
