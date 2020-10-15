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

package org.springframework.cloud.sleuth.instrument.web.client;

import java.util.concurrent.atomic.AtomicReference;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Subscription;

import org.springframework.cloud.sleuth.api.Span;
import org.springframework.cloud.sleuth.instrument.web.client.TraceExchangeFilterFunction.TraceWebClientSubscription;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith(MockitoExtension.class)
public class TraceWebClientBeanPostProcessorTest {

	@Mock
	ConfigurableApplicationContext springContext;

	@Mock
	Subscription subscription;

	@Mock
	Span span;

	@Test
	void should_add_filter_only_once_to_web_client() {
		TraceWebClientBeanPostProcessor processor = new TraceWebClientBeanPostProcessor(this.springContext);
		WebClient client = WebClient.create();

		client = (WebClient) processor.postProcessAfterInitialization(client, "foo");
		client = (WebClient) processor.postProcessAfterInitialization(client, "foo");

		client.mutate().filters(filters -> {
			BDDAssertions.then(filters).hasSize(1);
			BDDAssertions.then(filters.get(0)).isInstanceOf(TraceExchangeFilterFunction.class);
		});
	}

	@Test
	void should_add_filter_only_once_to_web_client_via_builder() {
		TraceWebClientBeanPostProcessor processor = new TraceWebClientBeanPostProcessor(this.springContext);
		WebClient.Builder builder = WebClient.builder();

		builder = (WebClient.Builder) processor.postProcessAfterInitialization(builder, "foo");
		builder = (WebClient.Builder) processor.postProcessAfterInitialization(builder, "foo");

		builder.build().mutate().filters(filters -> {
			BDDAssertions.then(filters).hasSize(1);
			BDDAssertions.then(filters.get(0)).isInstanceOf(TraceExchangeFilterFunction.class);
		});
	}

	@Test
	void should_close_span_on_cancel() {
		TraceWebClientSubscription traceSubscription = new TraceWebClientSubscription(subscription,
				new AtomicReference<>(span));

		traceSubscription.request(1);
		traceSubscription.cancel();

		Mockito.verify(span).error(TraceWebClientSubscription.CANCELLED_ERROR);
		Mockito.verify(span).end();

		// Check that the ref is clear following span completion
		Assertions.assertThat(traceSubscription.pendingSpan.get()).isNull();
	}

	@Test
	void should_not_crash_on_cancel_when_span_clear() {
		TraceWebClientSubscription traceSubscription = new TraceWebClientSubscription(subscription,
				new AtomicReference<>());

		traceSubscription.request(1);
		traceSubscription.cancel();

		Mockito.verify(subscription).request(1);
		Mockito.verify(subscription).cancel();
	}

}
