/*
 * Copyright 2013-2017 the original author or authors.
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

package org.springframework.cloud.brave.instrument.async.issues.issue410;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.sampler.Sampler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.brave.instrument.async.LazyTraceExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.sleuth.assertions.SleuthAssertions.then;

/**
 * @author Marcin Grzejszczak
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class, webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
		"ribbon.eureka.enabled=false", "feign.hystrix.enabled=false" })
public class Issue410Tests {

	private static final Log log = LogFactory
			.getLog(MethodHandles.lookup().lookupClass());

	@Autowired Environment environment;
	@Autowired Tracing tracing;
	@Autowired AsyncTask asyncTask;
	@Autowired RestTemplate restTemplate;
	/**
	 * Related to issue #445
	 */
	@Autowired
	Application.MyService executorService;

	@Test
	public void should_pass_tracing_info_for_tasks_running_without_a_pool() {
		Span span = this.tracing.tracer().nextSpan().name("foo");
		log.info("Starting test");
		try(Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			String response = this.restTemplate.getForObject(
					"http://localhost:" + port() + "/without_pool", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId())
						.isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}
	}

	@Test
	public void should_pass_tracing_info_for_tasks_running_with_a_pool() {
		Span span = this.tracing.tracer().nextSpan().name("foo");
		log.info("Starting test");
		try(Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			String response = this.restTemplate.getForObject(
					"http://localhost:" + port() + "/with_pool", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId())
						.isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}
	}

	/**
	 * Related to issue #423
	 */
	@Test
	public void should_pass_tracing_info_for_completable_futures_with_executor() {
		Span span = this.tracing.tracer().nextSpan().name("foo");
		log.info("Starting test");
		try(Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			String response = this.restTemplate.getForObject(
					"http://localhost:" + port() + "/completable", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId())
						.isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}
	}

	/**
	 * Related to issue #423
	 */
	@Test
	public void should_pass_tracing_info_for_completable_futures_with_task_scheduler() {
		Span span = this.tracing.tracer().nextSpan().name("foo");
		log.info("Starting test");
		try(Tracer.SpanInScope ws = this.tracing.tracer().withSpanInScope(span)) {
			String response = this.restTemplate.getForObject(
					"http://localhost:" + port() + "/taskScheduler", String.class);

			then(response).isEqualTo(span.context().traceIdString());
			Awaitility.await().untilAsserted(() -> {
				then(this.asyncTask.getSpan().get()).isNotNull();
				then(this.asyncTask.getSpan().get().context().traceId())
						.isEqualTo(span.context().traceId());
			});
		}
		finally {
			span.finish();
		}
	}

	private int port() {
		return this.environment.getProperty("local.server.port", Integer.class);
	}
}

@Configuration
@EnableAsync
class AppConfig {

	@Bean
	public Sampler testSampler() {
		return Sampler.ALWAYS_SAMPLE;
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	public Executor poolTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.initialize();
		return executor;
	}

}

@Component
class AsyncTask {

	private static final Log log = LogFactory.getLog(
			AsyncTask.class);

	private AtomicReference<Span> span = new AtomicReference<>();

	@Autowired 
	Tracing tracing;
	@Autowired
	@Qualifier("poolTaskExecutor")
	Executor executor;
	@Autowired
	@Qualifier("taskScheduler")
	Executor taskScheduler;
	@Autowired
	BeanFactory beanFactory;

	@Async("poolTaskExecutor")
	public void runWithPool() {
		log.info("This task is running with a pool.");
		this.span.set(this.tracing.tracer().currentSpan());
	}

	@Async
	public void runWithoutPool() {
		log.info("This task is running without a pool.");
		this.span.set(this.tracing.tracer().currentSpan());
	}

	public Span completableFutures() throws ExecutionException, InterruptedException {
		log.info("This task is running with completable future");
		CompletableFuture<Span> span1 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("First completable future");
			return AsyncTask.this.tracing.tracer().currentSpan();
		}, AsyncTask.this.executor);
		CompletableFuture<Span> span2 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("Second completable future");
			return AsyncTask.this.tracing.tracer().currentSpan();
		}, AsyncTask.this.executor);
		CompletableFuture<Span> response = CompletableFuture.allOf(span1, span2)
				.thenApply(ignoredVoid -> {
					AsyncTask.log.info("Third completable future");
					Span joinedSpan1 = span1.join();
					Span joinedSpan2 = span2.join();
					then(joinedSpan2).isNotNull();
					then(joinedSpan1.context().traceId()).isEqualTo(joinedSpan2.context().traceId());
					AsyncTask.log.info("TraceIds are correct");
					return joinedSpan2;
				});
		this.span.set(response.get());
		return this.span.get();
	}

	public Span taskScheduler() throws ExecutionException, InterruptedException {
		log.info("This task is running with completable future");
		CompletableFuture<Span> span1 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("First completable future");
			return AsyncTask.this.tracing.tracer().currentSpan();
		}, new LazyTraceExecutor(
				AsyncTask.this.beanFactory,
				AsyncTask.this.taskScheduler));
		CompletableFuture<Span> span2 = CompletableFuture.supplyAsync(() -> {
			AsyncTask.log.info("Second completable future");
			return AsyncTask.this.tracing.tracer().currentSpan();
		}, new LazyTraceExecutor(
				AsyncTask.this.beanFactory,
				AsyncTask.this.taskScheduler));
		CompletableFuture<Span> response = CompletableFuture.allOf(span1, span2)
				.thenApply(ignoredVoid -> {
					AsyncTask.log.info("Third completable future");
					Span joinedSpan1 = span1.join();
					Span joinedSpan2 = span2.join();
					then(joinedSpan2).isNotNull();
					then(joinedSpan1.context().traceId()).isEqualTo(joinedSpan2.context().traceId());
					AsyncTask.log.info("TraceIds are correct");
					return joinedSpan2;
				});
		this.span.set(response.get());
		return this.span.get();
	}

	public AtomicReference<Span> getSpan() {
		return span;
	}
}

@SpringBootApplication(exclude = SpringDataWebAutoConfiguration.class)
@RestController
class Application {

	private static final Log log = LogFactory.getLog(
			Application.class);

	@Autowired AsyncTask asyncTask;
	@Autowired Tracing tracing;

	@RequestMapping("/with_pool")
	public String withPool() {
		log.info("Executing with pool.");
		this.asyncTask.runWithPool();
		return this.tracing.tracer().currentSpan().context().traceIdString();

	}

	@RequestMapping("/without_pool")
	public String withoutPool() {
		log.info("Executing without pool.");
		this.asyncTask.runWithoutPool();
		return this.tracing.tracer().currentSpan().context().traceIdString();
	}

	@RequestMapping("/completable")
	public String completable() throws ExecutionException, InterruptedException {
		log.info("Executing completable");
		return this.asyncTask.completableFutures().context().traceIdString();
	}

	@RequestMapping("/taskScheduler")
	public String taskScheduler() throws ExecutionException, InterruptedException {
		log.info("Executing completable via task scheduler");
		return this.asyncTask.taskScheduler().context().traceIdString();
	}

	/**
	 * Related to issue #445
	 */
	@Bean
	public MyService executorService() {
		return new MyService() {
			@Override
			public void execute(Runnable command) {

			}
		};
	}

	interface MyService extends Executor {

	}

}
