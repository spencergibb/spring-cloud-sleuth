/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.rxjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import rx.functions.Action0;
import rx.plugins.RxJavaErrorHandler;
import rx.plugins.RxJavaObservableExecutionHook;
import rx.plugins.RxJavaPlugins;
import rx.plugins.RxJavaSchedulersHook;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * {@link RxJavaSchedulersHook} that wraps an {@link Action0} into its tracing
 * representation.
 *
 * @author Shivang Shah
 * @since 1.0.0
 */
public class SleuthRxJavaSchedulersHook extends RxJavaSchedulersHook {

	private static final Log log = LogFactory.getLog(SleuthRxJavaSchedulersHook.class);

	private static final String RXJAVA_COMPONENT = "rxjava";

	private final Tracer tracer;

	private final List<Pattern> threadsToIgnore;

	private RxJavaSchedulersHook delegate;

	public SleuthRxJavaSchedulersHook(Tracer tracer, List<String> threadsToIgnore) {
		this.tracer = tracer;
		this.threadsToIgnore = toPatternList(threadsToIgnore);
		try {
			this.delegate = RxJavaPlugins.getInstance().getSchedulersHook();
			if (this.delegate instanceof SleuthRxJavaSchedulersHook) {
				return;
			}
			RxJavaErrorHandler errorHandler = RxJavaPlugins.getInstance().getErrorHandler();
			RxJavaObservableExecutionHook observableExecutionHook = RxJavaPlugins.getInstance()
					.getObservableExecutionHook();
			logCurrentStateOfRxJavaPlugins(errorHandler, observableExecutionHook);
			RxJavaPlugins.getInstance().reset();
			RxJavaPlugins.getInstance().registerSchedulersHook(this);
			RxJavaPlugins.getInstance().registerErrorHandler(errorHandler);
			RxJavaPlugins.getInstance().registerObservableExecutionHook(observableExecutionHook);
		}
		catch (Exception ex) {
			log.error("Failed to register Sleuth RxJava SchedulersHook", ex);
		}
	}

	private List<Pattern> toPatternList(List<String> threadsToIgnore) {
		if (threadsToIgnore == null || threadsToIgnore.size() == 0) {
			return Collections.emptyList();
		}
		List<Pattern> patterns = new ArrayList<>(threadsToIgnore.size());
		for (String thread : threadsToIgnore) {
			patterns.add(Pattern.compile(thread));
		}
		return Collections.unmodifiableList(patterns);
	}

	private void logCurrentStateOfRxJavaPlugins(RxJavaErrorHandler errorHandler,
			RxJavaObservableExecutionHook observableExecutionHook) {
		if (log.isDebugEnabled()) {
			log.debug("Current RxJava plugins configuration is [" + "schedulersHook [" + this.delegate + "],"
					+ "errorHandler [" + errorHandler + "]," + "observableExecutionHook [" + observableExecutionHook
					+ "]," + "]");
			log.debug("Registering Sleuth RxJava Schedulers Hook.");
		}
	}

	@Override
	public Action0 onSchedule(Action0 action) {
		if (action instanceof TraceAction) {
			return action;
		}
		Action0 wrappedAction = this.delegate != null ? this.delegate.onSchedule(action) : action;
		if (wrappedAction instanceof TraceAction) {
			return action;
		}
		return super.onSchedule(new TraceAction(this.tracer, wrappedAction, this.threadsToIgnore));
	}

	/**
	 * Wrapped Action element.
	 *
	 * @author Marcin Grzejszczak
	 */
	static class TraceAction implements Action0 {

		private final Action0 actual;

		private final Tracer tracer;

		private final Span parent;

		private final List<Pattern> threadsToIgnore;

		TraceAction(Tracer tracer, Action0 actual, List<Pattern> threadsToIgnore) {
			this.tracer = tracer;
			this.threadsToIgnore = threadsToIgnore;
			this.parent = this.tracer.currentSpan();
			this.actual = actual;
		}

		@SuppressWarnings("Duplicates")
		@Override
		public void call() {
			// don't create a span if the thread name is on a list of threads to ignore
			String threadName = Thread.currentThread().getName();
			for (Pattern threadToIgnore : this.threadsToIgnore) {
				if (threadToIgnore.matcher(threadName).matches()) {
					if (log.isTraceEnabled()) {
						log.trace(String.format(
								"Thread with name [%s] matches the regex [%s]. A span will not be created for this Thread.",
								threadName, threadToIgnore));
					}
					this.actual.call();
					return;
				}
			}
			Span span = this.parent;
			boolean created = false;
			if (span == null) {
				span = SleuthRxJavaSpan.RX_JAVA_TRACE_ACTION_SPAN.wrap(this.tracer.nextSpan())
						.name(SleuthRxJavaSpan.RX_JAVA_TRACE_ACTION_SPAN.getName())
						.tag(SleuthRxJavaSpan.Tags.THREAD, Thread.currentThread().getName()).start();
				created = true;
			}
			try (Tracer.SpanInScope ws = this.tracer.withSpan(span)) {
				this.actual.call();
			}
			finally {
				if (created) {
					span.end();
				}
			}
		}

	}

}
