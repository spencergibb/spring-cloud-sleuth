/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.sleuth.trace;

import static org.springframework.cloud.sleuth.util.ExceptionUtils.warn;

import java.util.Random;
import java.util.concurrent.Callable;

import org.springframework.cloud.sleuth.MilliSpan;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Trace;
import org.springframework.cloud.sleuth.TraceManager;
import org.springframework.cloud.sleuth.event.SpanAcquiredEvent;
import org.springframework.cloud.sleuth.event.SpanContinuedEvent;
import org.springframework.cloud.sleuth.event.SpanReleasedEvent;
import org.springframework.cloud.sleuth.instrument.TraceCallable;
import org.springframework.cloud.sleuth.instrument.TraceRunnable;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.ApplicationEventPublisher;

/**
 * @author Spencer Gibb
 */
public class DefaultTraceManager implements TraceManager {

	private final Sampler<Void> defaultSampler;

	private final ApplicationEventPublisher publisher;

	private final Random random;

	public DefaultTraceManager(Sampler<Void> defaultSampler,
							   Random random, ApplicationEventPublisher publisher) {
		this.defaultSampler = defaultSampler;
		this.random = random;
		this.publisher = publisher;
	}

	@Override
	public Trace startSpan(String name, Span parent) {
		if (parent == null) {
			return startSpan(name);
		}
		Span currentSpan = getCurrentSpan();
		if (currentSpan != null && !parent.equals(currentSpan)) {
			warn("Trace client warn: thread " + Thread.currentThread().getName()
					+ " tried to start a new Span " + "with parent " + parent.toString()
					+ ", but there is already a " + "currentSpan " + currentSpan);
		}
		return continueSpan(createChild(parent, name));
	}

	@Override
	public Trace startSpan(String name) {
		return this.startSpan(name, this.defaultSampler, null);
	}

	@Override
	public <T> Trace startSpan(String name, Sampler<T> s, T info) {
		Span span = null;
		if (isTracing() || s.next(info)) {
			span = createChild(getCurrentSpan(), name);
		}
		else {
			// Non-exportable so we keep the trace but not other data
			long id = createId();
			span = MilliSpan.builder().begin(System.currentTimeMillis()).name(name)
					.traceId(id).spanId(id).exportable(false).build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, span));
		}
		return continueSpan(span);
	}

	@Override
	public Trace detach(Trace trace) {
		if (trace == null) {
			return null;
		}
		Span cur = TraceContextHolder.getCurrentSpan();
		Span span = trace.getSpan();
		if (cur != span) {
			ExceptionUtils.warn("Tried to detach trace span but "
					+ "it is not the current span for the '"
					+ Thread.currentThread().getName() + "' thread: " + span
					+ ". You have " + "probably forgotten to close or detach " + cur);
		}
		else {
			if (trace.getSaved() != null) {
				TraceContextHolder.setCurrentTrace(trace.getSaved());
			}
			else {
				TraceContextHolder.removeCurrentTrace();
			}
		}
		return trace.getSaved();
	}

	@Override
	public Trace close(Trace trace) {
		if (trace == null) {
			return null;
		}
		Span cur = TraceContextHolder.getCurrentSpan();
		Span span = trace.getSpan();
		Trace savedTrace = trace.getSaved();
		if (cur != span) {
			ExceptionUtils.warn("Tried to close trace span but "
					+ "it is not the current span for the '"
					+ Thread.currentThread().getName() + "' thread" + span
					+ ".  You have " + "probably forgotten to close or detach " + cur);
		}
		else {
			if (span != null) {
				span.stop();
				if (savedTrace != null
						&& span.getParents().contains(savedTrace.getSpan().getSpanId())) {
					this.publisher.publishEvent(
							new SpanReleasedEvent(this, savedTrace.getSpan(), span));
					TraceContextHolder.setCurrentTrace(savedTrace);
				}
				else {
					if (!span.isRemote()) {
						this.publisher.publishEvent(new SpanReleasedEvent(this, span));
					}
					TraceContextHolder.removeCurrentTrace();
				}
			}
			else {
				TraceContextHolder.removeCurrentTrace();
			}
		}
		return savedTrace;
	}

	protected Span createChild(Span parent, String name) {
		long id = createId();
		if (parent == null) {
			MilliSpan span = MilliSpan.builder().begin(System.currentTimeMillis())
					.name(name).traceId(id).spanId(id).build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, span));
			return span;
		}
		else {
			if (TraceContextHolder.getCurrentTrace() == null) {
				Trace trace = createTrace(null, parent);
				TraceContextHolder.setCurrentTrace(trace);
			}
			MilliSpan span = MilliSpan.builder().begin(System.currentTimeMillis())
					.name(name).traceId(parent.getTraceId()).parent(parent.getSpanId())
					.spanId(id).processId(parent.getProcessId()).build();
			this.publisher.publishEvent(new SpanAcquiredEvent(this, parent, span));
			return span;
		}
	}

	private long createId() {
		return random.nextLong();
	}

	@Override
	public Trace continueSpan(Span span) {
		if (span != null) {
			this.publisher.publishEvent(new SpanContinuedEvent(this, span));
		}
		Trace trace = createTrace(TraceContextHolder.getCurrentTrace(), span);
		TraceContextHolder.setCurrentTrace(trace);
		return trace;
	}

	protected Trace createTrace(Trace trace, Span span) {
		return new Trace(trace, span);
	}

	@Override
	public Span getCurrentSpan() {
		return TraceContextHolder.getCurrentSpan();
	}

	@Override
	public boolean isTracing() {
		return TraceContextHolder.isTracing();
	}

	@Override
	public void addAnnotation(String key, String value) {
		Span s = getCurrentSpan();
		if (s != null && s.isExportable()) {
			s.tag(key, value);
		}
	}

	/**
	 * Wrap the callable in a TraceCallable, if tracing.
	 *
	 * @return The callable provided, wrapped if tracing, 'callable' if not.
	 */
	@Override
	public <V> Callable<V> wrap(Callable<V> callable) {
		if (isTracing()) {
			return new TraceCallable<>(this, callable);
		}
		return callable;
	}

	/**
	 * Wrap the runnable in a TraceRunnable, if tracing.
	 *
	 * @return The runnable provided, wrapped if tracing, 'runnable' if not.
	 */
	@Override
	public Runnable wrap(Runnable runnable) {
		if (isTracing()) {
			return new TraceRunnable(this, runnable);
		}
		return runnable;
	}

}
