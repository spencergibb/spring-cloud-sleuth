/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.stream;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.metric.SpanMetricReporter;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

/**
 * @author Marcin Grzejszczak
 */
public class StreamSpanReporterTests {

	HostLocator endpointLocator = Mockito.mock(HostLocator.class);
	SpanMetricReporter spanMetricReporter = Mockito.mock(SpanMetricReporter.class);
	MockEnvironment mockEnvironment = new MockEnvironment();
	StreamSpanReporter reporter;

	@Before
	public void setup() {
		this.reporter = new StreamSpanReporter(this.endpointLocator, this.spanMetricReporter, this.mockEnvironment);
	}

	@Test
	public void should_not_throw_an_exception_when_queue_size_is_exceeded() throws Exception {
		ArrayBlockingQueue<Span> queue = new ArrayBlockingQueue<>(1);
		queue.add(Span.builder().name("foo").build());
		this.reporter.setQueue(queue);

		this.reporter.report(Span.builder().name("bar").exportable(true).build());

		then(this.spanMetricReporter).should().incrementDroppedSpans(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_append_client_serviceid_when_span_has_cr_event() throws Exception {
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		this.mockEnvironment.setProperty("spring.application.instanceid", "foo");
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.CLIENT_RECV);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.extracting(o -> ((Map<String, String>) o).get(Span.SPAN_CLIENT_INSTANCEID))
				.containsExactly("foo");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_append_client_serviceid_when_span_has_cs_event() throws Exception {
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		this.mockEnvironment.setProperty("spring.application.instanceid", "foo");
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.CLIENT_SEND);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.extracting(o -> ((Map<String, String>) o).get(Span.SPAN_CLIENT_INSTANCEID))
				.containsExactly("foo");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_append_server_serviceid_when_span_has_sr_event() throws Exception {
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		this.mockEnvironment.setProperty("spring.application.instanceid", "foo");
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.SERVER_RECV);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.extracting(o -> ((Map<String, String>) o).get(Span.SPAN_SERVER_INSTANCEID))
				.containsExactly("foo");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_append_server_serviceid_when_span_has_ss_event() throws Exception {
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		this.mockEnvironment.setProperty("spring.application.instanceid", "foo");
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.SERVER_SEND);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.extracting(o -> ((Map<String, String>) o).get(Span.SPAN_SERVER_INSTANCEID))
				.containsExactly("foo");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_not_append_server_serviceid_when_span_has_ss_event_and_there_is_no_environment() throws Exception {
		this.reporter = new StreamSpanReporter(this.endpointLocator, this.spanMetricReporter, null);
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.SERVER_SEND);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.filteredOn(o -> ((Map<String, String>) o).containsKey(Span.SPAN_SERVER_INSTANCEID))
				.isNullOrEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void should_not_append_server_serviceid_when_span_has_cs_event_and_there_is_no_environment() throws Exception {
		this.reporter = new StreamSpanReporter(this.endpointLocator, this.spanMetricReporter, null);
		LinkedBlockingQueue<Span> queue = new LinkedBlockingQueue<>(1000);
		this.reporter.setQueue(queue);
		Span span = Span.builder().name("bar").exportable(true).build();
		span.logEvent(Span.CLIENT_SEND);

		this.reporter.report(span);

		assertThat(queue).isNotEmpty();
		assertThat(queue.poll())
				.extracting(Span::tags)
				.filteredOn(o -> ((Map<String, String>) o).containsKey(Span.SPAN_CLIENT_INSTANCEID))
				.isNullOrEmpty();
	}

}