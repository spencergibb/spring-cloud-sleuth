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

package org.springframework.cloud.sleuth.tracer;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * A noop implementation. Does nothing.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class SimpleCurrentTraceContext implements CurrentTraceContext {

	public TraceContext traceContext;

	@Override
	public TraceContext context() {
		return this.traceContext;
	}

	@Override
	public Scope newScope(TraceContext context) {
		this.traceContext = context;
		return () -> {
		};
	}

	@Override
	public Scope maybeScope(TraceContext context) {
		this.traceContext = context;
		return () -> {
		};
	}

	@Override
	public <C> Callable<C> wrap(Callable<C> task) {
		return task;
	}

	@Override
	public Runnable wrap(Runnable task) {
		return task;
	}

	@Override
	public Executor wrap(Executor delegate) {
		return delegate;
	}

	@Override
	public ExecutorService wrap(ExecutorService delegate) {
		return delegate;
	}

	public static SimpleCurrentTraceContext withTracer(SimpleTracer simpleTracer) {
		return new SimpleCurrentTraceContext() {
			@Override
			public TraceContext context() {
				return simpleTracer.currentSpan() != null ? simpleTracer.currentSpan().context() : null;
			}
		};
	}

}
