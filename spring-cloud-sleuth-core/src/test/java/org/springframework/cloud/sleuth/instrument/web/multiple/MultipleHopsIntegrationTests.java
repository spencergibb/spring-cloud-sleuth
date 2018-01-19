package org.springframework.cloud.sleuth.instrument.web.multiple;

import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.util.ArrayListSpanReporter;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

@RunWith(SpringJUnit4ClassRunner.class)
@TestPropertySource(properties = {
		"spring.application.name=multiplehopsintegrationtests",
		"spring.sleuth.http.legacy.enabled=true"
})
@SpringBootTest(classes = MultipleHopsIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("baggage")
public class MultipleHopsIntegrationTests {

	@Autowired Tracing tracing;
	@Autowired TraceKeys traceKeys;
	@Autowired ArrayListSpanReporter reporter;
	@Autowired RestTemplate restTemplate;
	@Autowired Config config;
	@Autowired DemoApplication application;

	@Before
	public void setup() {
		this.reporter.clear();
	}

	@Test
	public void should_prepare_spans_for_export() throws Exception {
		this.restTemplate.getForObject("http://localhost:" + this.config.port + "/greeting", String.class);

		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.reporter.getSpans()).hasSize(5);
		});
		then(this.reporter.getSpans().stream().map(zipkin2.Span::name)
				.collect(toList())).containsAll(asList("http:/greeting", "send"));
		then(this.reporter.getSpans().stream()
				.map(span -> span.tags().get("channel"))
				.filter(Objects::nonNull)
				.distinct()
				.collect(toList()))
				.hasSize(3)
				.containsAll(asList("send:words", "send:counts", "send:greetings"));
	}

	// issue #237 - baggage
	@Test
	@Ignore
	public void should_propagate_the_baggage() throws Exception {
		//tag::baggage[]
		Span initialSpan = this.tracing.tracer().nextSpan().name("span").start();
		initialSpan.tag("foo", "bar");
		initialSpan.tag("UPPER_CASE", "someValue");
		//end::baggage[]

		try (Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(initialSpan)) {
			HttpHeaders headers = new HttpHeaders();
			headers.put("baz", Collections.singletonList("baz"));
			headers.put("bizarreCASE", Collections.singletonList("value"));
			RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET,
					URI.create("http://localhost:" + this.config.port + "/greeting"));
			this.restTemplate.exchange(requestEntity, String.class);
		} finally {
			initialSpan.finish();
		}
		await().atMost(5, SECONDS).untilAsserted(() -> {
			then(this.reporter.getSpans()).isNotEmpty();
		});

		then(this.application.allSpans()).as("All have foo")
				.allMatch(span -> "bar".equals(baggage(span, "foo")));
		then(this.application.allSpans()).as("All have UPPER_CASE")
				.allMatch(span -> "someValue".equals(baggage(span, "UPPER_CASE")));
		then(this.application.allSpans()
				.stream()
				.filter(span -> "baz".equals(baggage(span, "baz")))
				.collect(Collectors.toList()))
				.as("Someone has baz")
				.isNotEmpty();
		then(this.application.allSpans()
				.stream()
				.filter(span -> "value".equals(baggage(span, "bizarreCASE")))
				.collect(Collectors.toList()))
				.isNotEmpty();
	}

	private String baggage(Span span, String name) {
		return ExtraFieldPropagation.get(span.context(), name);
	}

	@Configuration
	@SpringBootApplication(exclude = JmxAutoConfiguration.class)
	public static class Config implements
			ApplicationListener<ServletWebServerInitializedEvent> {
		int port;

		@Override
		public void onApplicationEvent(ServletWebServerInitializedEvent event) {
			this.port = event.getSource().getPort();
		}

		@Bean
		RestTemplate restTemplate() {
			return new RestTemplate();
		}

		@Bean ArrayListSpanReporter arrayListSpanAccumulator() {
			return new ArrayListSpanReporter();
		}

		@Bean Sampler defaultTraceSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}
	}
}
