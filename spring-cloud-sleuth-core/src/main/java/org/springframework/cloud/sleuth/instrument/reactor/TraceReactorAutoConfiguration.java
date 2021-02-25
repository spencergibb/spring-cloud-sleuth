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

package org.springframework.cloud.sleuth.instrument.reactor;

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;
import java.util.function.Function;

import javax.annotation.PreDestroy;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.cloud.sleuth.instrument.web.TraceWebFluxAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

import static org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth.scopePassingSpanOperator;
import static org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY;
import static org.springframework.cloud.sleuth.instrument.reactor.TraceReactorAutoConfiguration.TraceReactorConfiguration.SLEUTH_TRACE_REACTOR_KEY;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} to enable tracing of Reactor components via Spring Cloud Sleuth.
 *
 * @author Stephane Maldini
 * @author Marcin Grzejszczak
 * @since 2.0.0
 * @deprecated This type should have never been public and will be hidden or removed in
 * 3.0
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "spring.sleuth.reactor.enabled", matchIfMissing = true)
@ConditionalOnClass(Mono.class)
@AutoConfigureAfter(TraceWebFluxAutoConfiguration.class)
@EnableConfigurationProperties(SleuthReactorProperties.class)
public class TraceReactorAutoConfiguration {

	static final String SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY = "sleuth";

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(Tracing.class)
	static class TraceReactorConfiguration {

		static final String SLEUTH_TRACE_REACTOR_KEY = TraceReactorConfiguration.class
				.getName();

		private static final Log log = LogFactory.getLog(TraceReactorConfiguration.class);

		static final boolean IS_QUEUE_WRAPPER_ON_THE_CLASSPATH = isQueueWrapperOnTheClasspath();

		@Autowired
		ConfigurableApplicationContext springContext;

		@PreDestroy
		public void cleanupHooks() {
			if (log.isTraceEnabled()) {
				log.trace("Cleaning up hooks");
			}
			SleuthReactorProperties reactorProperties = this.springContext
					.getBean(SleuthReactorProperties.class);
			if (TraceReactorAutoConfiguration.TraceReactorConfiguration.IS_QUEUE_WRAPPER_ON_THE_CLASSPATH
					&& reactorProperties.isDecorateHooks()) {
				if (log.isTraceEnabled()) {
					log.trace("Resetting queue wrapper instrumentation");
				}
				Hooks.removeQueueWrapper(SLEUTH_TRACE_REACTOR_KEY);
			}
			if (reactorProperties.isDecorateOnEach()) {
				if (log.isTraceEnabled()) {
					log.trace("Resetting onEach operator instrumentation");
				}
				Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace("Resetting onLast operator instrumentation");
				}
				Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
			}
		}

		private static boolean isQueueWrapperOnTheClasspath() {
			return ReflectionUtils.findMethod(Hooks.class, "addQueueWrapper",
					String.class, Function.class) != null;
		}

		@Bean
		@ConditionalOnMissingBean
		HookRegisteringBeanDefinitionRegistryPostProcessor traceHookRegisteringBeanDefinitionRegistryPostProcessor(
				ConfigurableApplicationContext context) {
			if (log.isTraceEnabled()) {
				log.trace(
						"Registering bean definition registry post processor for context ["
								+ context + "]");
			}
			return new HookRegisteringBeanDefinitionRegistryPostProcessor(context);
		}

		@Configuration
		@ConditionalOnClass(RefreshScope.class)
		static class HooksRefresherConfiguration {

			@Bean
			HooksRefresher hooksRefresher(SleuthReactorProperties reactorProperties,
					ConfigurableApplicationContext context) {
				return new HooksRefresher(reactorProperties, context);
			}

		}

	}

}

class HooksRefresher implements ApplicationListener<RefreshScopeRefreshedEvent> {

	private static final Log log = LogFactory.getLog(HooksRefresher.class);

	private final SleuthReactorProperties reactorProperties;

	private final ConfigurableApplicationContext context;

	HooksRefresher(SleuthReactorProperties reactorProperties,
			ConfigurableApplicationContext context) {
		this.reactorProperties = reactorProperties;
		this.context = context;
	}

	@Override
	public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
		if (log.isDebugEnabled()) {
			log.debug("Context refreshed, will reset hooks and then re-register them");
		}
		Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.removeQueueWrapper(SLEUTH_TRACE_REACTOR_KEY);
		if (this.reactorProperties.isDecorateHooks()
				&& TraceReactorAutoConfiguration.TraceReactorConfiguration.IS_QUEUE_WRAPPER_ON_THE_CLASSPATH) {
			if (log.isTraceEnabled()) {
				log.trace("Adding queue wrapper instrumentation");
			}
			HookRegisteringBeanDefinitionRegistryPostProcessor.addQueueWrapper(context);
		}
		else if (this.reactorProperties.isDecorateOnEach()) {
			if (log.isTraceEnabled()) {
				log.trace("Decorating onEach operator instrumentation");
			}
			Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY,
					scopePassingSpanOperator(this.context));
			Schedulers.onScheduleHook(
					TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
					ReactorSleuth.scopePassingOnScheduleHook(this.context));
		}
		else {
			if (log.isTraceEnabled()) {
				log.trace("Decorating onLast operator instrumentation");
			}
			Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY,
					scopePassingSpanOperator(this.context));
		}
	}

}

class HookRegisteringBeanDefinitionRegistryPostProcessor
		implements BeanDefinitionRegistryPostProcessor, Closeable {

	private static final Log log = LogFactory
			.getLog(HookRegisteringBeanDefinitionRegistryPostProcessor.class);

	final ConfigurableApplicationContext springContext;

	HookRegisteringBeanDefinitionRegistryPostProcessor(
			ConfigurableApplicationContext springContext) {
		this.springContext = springContext;
	}

	@Override
	public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		setupHooks(this.springContext);
	}

	static void setupHooks(ConfigurableApplicationContext springContext) {
		ConfigurableEnvironment environment = springContext.getEnvironment();
		Boolean decorateHooks = environment
				.getProperty("spring.sleuth.reactor.decorate-hooks", Boolean.class);
		if (wrapperNotOnClasspathButPropertyHasValue(decorateHooks)) {
			log.warn(
					"You have explicitly set the decorate hooks option but you're using an old version of Reactor. Please upgrade to the latest Boot version (at least 2.3.9.RELEASE). Will fall back to the previous reactor instrumentation mode");
		}
		else {
			decorateHooks = decorateHooks != null ? decorateHooks : Boolean.TRUE;
		}
		if (wrapperOnClasspathHooksPropertyTurnedOn(decorateHooks)) {
			if (log.isTraceEnabled()) {
				log.trace("Adding queue wrapper instrumentation");
			}
			addQueueWrapper(springContext);
		}
		else {
			boolean decorateOnEach = environment.getProperty(
					"spring.sleuth.reactor.decorate-on-each", Boolean.class, true);
			if (decorateOnEach) {
				if (log.isTraceEnabled()) {
					log.trace("Decorating onEach operator instrumentation");
				}
				Hooks.onEachOperator(SLEUTH_TRACE_REACTOR_KEY,
						scopePassingSpanOperator(springContext));
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace("Decorating onLast operator instrumentation");
				}
				Hooks.onLastOperator(SLEUTH_TRACE_REACTOR_KEY,
						scopePassingSpanOperator(springContext));
			}
		}
		decorateScheduler(springContext);
	}

	private static boolean wrapperOnClasspathHooksPropertyTurnedOn(Boolean decorateHooks) {
		return Boolean.TRUE.equals(decorateHooks)
				&& TraceReactorAutoConfiguration.TraceReactorConfiguration.IS_QUEUE_WRAPPER_ON_THE_CLASSPATH;
	}

	private static boolean wrapperNotOnClasspathButPropertyHasValue(Boolean decorateHooks) {
		return !TraceReactorAutoConfiguration.TraceReactorConfiguration.IS_QUEUE_WRAPPER_ON_THE_CLASSPATH
				&& decorateHooks != null;
	}

	static void addQueueWrapper(ConfigurableApplicationContext springContext) {
		Hooks.addQueueWrapper(SLEUTH_TRACE_REACTOR_KEY,
				queue -> traceQueue(springContext, queue));
	}

	@Override
	public void close() throws IOException {
		if (log.isTraceEnabled()) {
			log.trace("Cleaning up hooks");
		}
		Hooks.resetOnEachOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.resetOnLastOperator(SLEUTH_TRACE_REACTOR_KEY);
		Hooks.removeQueueWrapper(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
		Schedulers.resetOnScheduleHook(SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
		Schedulers.resetOnScheduleHook(
				TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY);
	}

	private static void decorateScheduler(ConfigurableApplicationContext springContext) {
		Schedulers.onScheduleHook(
				TraceReactorAutoConfiguration.SLEUTH_REACTOR_EXECUTOR_SERVICE_KEY,
				ReactorSleuth.scopePassingOnScheduleHook(springContext));
	}

	private static Queue<?> traceQueue(ConfigurableApplicationContext springContext,
			Queue<?> queue) {
		if (!springContext.isActive()) {
			return queue;
		}
		CurrentTraceContext currentTraceContext = springContext
				.getBean(CurrentTraceContext.class);
		@SuppressWarnings("unchecked")
		Queue envelopeQueue = queue;
		return new AbstractQueue<Object>() {

			@Override
			public int size() {
				return envelopeQueue.size();
			}

			@Override
			public boolean offer(Object o) {
				TraceContext traceContext = currentTraceContext.get();
				return envelopeQueue.offer(new Envelope(o, traceContext));
			}

			@Override
			public Object poll() {
				Object object = envelopeQueue.poll();
				if (object == null) {
					return null;
				}
				else if (object instanceof Envelope) {
					Envelope envelope = (Envelope) object;
					restoreTheContext(envelope);
					return envelope.body;
				}
				return object;
			}

			private void restoreTheContext(Envelope envelope) {
				if (envelope.traceContext != null) {
					currentTraceContext.maybeScope(envelope.traceContext);
				}
			}

			@Override
			public Object peek() {
				Object peek = queue.peek();
				if (peek instanceof Envelope) {
					Envelope envelope = (Envelope) peek;
					restoreTheContext(envelope);
					return (envelope).body;
				}
				return peek;
			}

			@Override
			@SuppressWarnings("unchecked")
			public Iterator<Object> iterator() {
				Iterator<?> iterator = queue.iterator();
				return new Iterator<Object>() {
					@Override
					public boolean hasNext() {
						return iterator.hasNext();
					}

					@Override
					public Object next() {
						Object next = iterator.next();
						if (next instanceof Envelope) {
							Envelope envelope = (Envelope) next;
							restoreTheContext(envelope);
							return (envelope).body;
						}
						return next;
					}
				};
			}
		};
	}

	static class Envelope {

		final Object body;

		final TraceContext traceContext;

		Envelope(Object body, TraceContext traceContext) {
			this.body = body;
			this.traceContext = traceContext;
		}

	}

}
