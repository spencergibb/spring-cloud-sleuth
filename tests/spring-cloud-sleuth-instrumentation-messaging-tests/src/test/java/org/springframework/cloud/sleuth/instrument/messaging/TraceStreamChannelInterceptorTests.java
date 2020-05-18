/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.messaging;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.sleuth.instrument.util.SpanUtil;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TraceStreamChannelInterceptorTests.App.class,
		properties = "spring.cloud.stream.source=testSupplier")
@DirtiesContext
public class TraceStreamChannelInterceptorTests {

	@Autowired
	private OutputDestination channel;

	@Autowired
	private Tracing tracing;

	@Autowired
	private StreamBridge streamBridge;

	@Autowired
	private TestSpanHandler spans;

	@After
	public void close() {
		this.spans.clear();
	}

	@Test
	public void testSpanPropagationViaBridge() {
		Span span = this.tracing.tracer().nextSpan().name("http:testSendMessage").start();
		String expectedSpanId = SpanUtil.idToHex(span.context().spanId());

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			this.streamBridge.send("testSupplier-out-0", "hi");
		}
		finally {
			span.finish();
		}

		assertThatNewSpanIdWasSetOnMessage(expectedSpanId);
	}

	private void assertThatNewSpanIdWasSetOnMessage(String expectedSpanId) {
		Message<?> message = this.channel.receive(0);
		assertThat(message).as("message was null").isNotNull();

		String spanId = message.getHeaders().get(TraceMessageHeaders.SPAN_ID_NAME,
				String.class);
		assertThat(spanId).as("spanId was equal to parent's id")
				.isNotEqualTo(expectedSpanId);

		String traceId = message.getHeaders().get(TraceMessageHeaders.TRACE_ID_NAME,
				String.class);
		assertThat(traceId).as("traceId was null").isNotNull();

		String parentId = message.getHeaders().get(TraceMessageHeaders.PARENT_ID_NAME,
				String.class);
		// [0] - producer
		// [1] - http:testsendmessage
		assertThat(parentId).as("parentId was not equal to parent's id")
				.isEqualTo(this.spans.get(1).id());
	}

	@Configuration
	@EnableAutoConfiguration
	@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
	static class App {

		@Bean
		Sampler testSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		@Bean
		SpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

	}

}
