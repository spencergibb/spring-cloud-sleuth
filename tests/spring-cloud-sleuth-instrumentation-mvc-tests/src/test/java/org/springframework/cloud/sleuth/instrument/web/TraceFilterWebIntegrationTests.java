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

package org.springframework.cloud.sleuth.instrument.web;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import brave.http.HttpRequest;
import brave.http.HttpRequestParser;
import brave.propagation.CurrentTraceContext;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import zipkin2.Span;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.sleuth.util.BlockingQueueSpanReporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@ExtendWith({ SpringExtension.class, OutputCaptureExtension.class })
@SpringBootTest(classes = TraceFilterWebIntegrationTests.Config.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.sleuth.http.legacy.enabled=true")
public class TraceFilterWebIntegrationTests {

	@Autowired
	CurrentTraceContext currentTraceContext;

	@Autowired
	BlockingQueueSpanReporter reporter;

	@Autowired
	@HttpServerSampler
	SamplerFunction<HttpRequest> sampler;

	@Autowired
	Environment environment;

	@AfterEach
	public void cleanup() {
		this.reporter.assertEmpty();
	}

	@Test
	public void should_tag_url() {
		new RestTemplate().getForObject("http://localhost:" + port() + "/good",
				String.class);

		then(this.currentTraceContext.get()).isNull();
		then(this.reporter.takeSpan().tags()).containsKey("http.url");
	}

	@Test
	public void should_not_create_a_span_for_error_controller(CapturedOutput capture) {
		try {
			new RestTemplate().getForObject("http://localhost:" + port() + "/",
					String.class);
			BDDAssertions.fail("should fail due to runtime exception");
		}
		catch (Exception e) {
		}

		then(this.currentTraceContext.get()).isNull();
		Span fromFirstTraceFilterFlow = this.reporter.takeSpan();
		then(fromFirstTraceFilterFlow.tags()).containsEntry("http.method", "GET")
				.containsEntry("mvc.controller.class", "ExceptionThrowingController")
				.containsEntry("error",
						"Request processing failed; nested exception is java.lang.RuntimeException: Throwing exception");
		// issue#714
		String hex = fromFirstTraceFilterFlow.traceId();
		String[] split = capture.toString().split("\n");
		List<String> list = Arrays.stream(split)
				.filter(s -> s.contains("Uncaught exception thrown"))
				.filter(s -> s.contains(hex + "," + hex + ",true]"))
				.collect(Collectors.toList());
		then(list).isNotEmpty();
	}

	@Test
	public void should_create_spans_for_endpoint_returning_unsuccessful_result() {
		try {
			new RestTemplate().getForObject(
					"http://localhost:" + port() + "/test_bad_request", String.class);
			fail("should throw exception");
		}
		catch (HttpClientErrorException e) {
		}

		then(this.currentTraceContext.get()).isNull();
		Span span = this.reporter.takeSpan();
		then(span.kind().ordinal()).isEqualTo(Span.Kind.SERVER.ordinal());
		then(span.tags()).containsEntry("http.status_code", "400");
		then(span.tags()).containsEntry("http.path", "/test_bad_request");
	}

	@Test
	public void should_inject_http_sampler() {
		then(this.sampler).isNotNull();
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}

	@EnableAutoConfiguration
	@Configuration
	public static class Config {

		@Bean
		GoodController goodController() {
			return new GoodController();
		}

		@Bean
		ExceptionThrowingController badController() {
			return new ExceptionThrowingController();
		}

		@Bean
		BlockingQueueSpanReporter reporter() {
			return new BlockingQueueSpanReporter();
		}

		@Bean
		Sampler alwaysSampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

		// tag::custom_parser[]
		@Bean(name = { HttpClientRequestParser.NAME, HttpServerRequestParser.NAME })
		HttpRequestParser sleuthHttpServerRequestParser() {
			return (req, context, span) -> {
				HttpRequestParser.DEFAULT.parse(req, context, span);
				String url = req.url();
				if (url != null) {
					span.tag("http.url", url);
				}
			};
		}
		// end::custom_parser[]

		// tag::custom_server_sampler[]
		@Bean(name = HttpServerSampler.NAME)
		SamplerFunction<HttpRequest> myHttpSampler(SkipPatternProvider provider) {
			Pattern pattern = provider.skipPattern();
			return request -> {
				String url = request.path();
				boolean shouldSkip = pattern.matcher(url).matches();
				if (shouldSkip) {
					return false;
				}
				return null;
			};
		}
		// end::custom_server_sampler[]

		@Bean
		RestTemplate restTemplate() {
			RestTemplate restTemplate = new RestTemplate();
			restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
				@Override
				public void handleError(ClientHttpResponse response) throws IOException {
				}
			});
			return restTemplate;
		}

	}

	@RestController
	public static class GoodController {

		@RequestMapping("/good")
		public String beGood() {
			return "good";
		}

	}

	@RestController
	public static class ExceptionThrowingController {

		@RequestMapping("/")
		public void throwException() {
			throw new RuntimeException("Throwing exception");
		}

		@RequestMapping(path = "/test_bad_request", method = RequestMethod.GET)
		public ResponseEntity<?> processFail() {
			return ResponseEntity.badRequest().build();
		}

	}

}
